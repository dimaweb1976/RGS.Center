package com.rgs.center.peripheral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Обслуживает одно внешнее устройство в отдельных RX- и TX-потоках.
 *
 * <p>Worker владеет транспортом, ограниченной очередью команд, кольцевым
 * приёмным буфером и очередью готовых кадров. Один экземпляр нельзя совместно
 * использовать для разных физических устройств.</p>
 */
public final class PeripheralDeviceWorker implements AutoCloseable {

    private static final Logger log =
            LoggerFactory.getLogger(PeripheralDeviceWorker.class);

    /** Постоянный идентификатор для диагностики и имён потоков. */
    private final String deviceId;

    /** Соединение с одним физическим устройством. */
    private final PeripheralTransport transport;

    /** Размеры очередей, буферов и порции чтения. */
    private final PeripheralWorkerConfiguration configuration;

    /** Стратегия выделения строкового или бинарного кадра. */
    private final PeripheralFrameDecoder frameDecoder;

    /** Буфер между низкоуровневым чтением и разбором протокола. */
    private final ByteRingBuffer receiveBuffer;

    /** Очередь обычных и приоритетных команд для TX-потока. */
    private final PeripheralCommandQueue commandQueue;

    /** Ограниченная очередь кадров, подготовленных для прикладного сервиса. */
    private final LinkedBlockingQueue<byte[]> frameQueue;

    /** Атомарное состояние жизненного цикла для чтения из других потоков. */
    private final AtomicReference<PeripheralWorkerState> state =
            new AtomicReference<>(PeripheralWorkerState.STOPPED);

    /** Флаг продолжения циклов RX/TX. */
    private final AtomicBoolean running = new AtomicBoolean();

    /** Потокобезопасные накопительные метрики worker. */
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong framesReceived = new AtomicLong();
    private final AtomicLong framesDropped = new AtomicLong();
    private final AtomicLong lastReceiveEpochMillis = new AtomicLong();
    private final AtomicLong lastTransmitEpochMillis = new AtomicLong();
    private final AtomicReference<String> lastError =
            new AtomicReference<>("");

    /** Запрещает смешивать ответы параллельных request/response операций. */
    private final ReentrantLock exchangeLock = new ReentrantLock();

    /** Исполнитель двух выделенных потоков; создаётся при каждом start(). */
    private ExecutorService executor;

    /**
     * Создаёт worker для строкового протокола CR/LF.
     *
     * @param deviceId идентификатор физического устройства
     * @param transport транспорт этого устройства
     * @param configuration ограничения очередей и буферов
     */
    public PeripheralDeviceWorker(
            String deviceId,
            PeripheralTransport transport,
            PeripheralWorkerConfiguration configuration
    ) {
        this(
                deviceId,
                transport,
                configuration,
                new LineFrameDecoder(configuration.maxFrameLength())
        );
    }

    /**
     * Создаёт worker с пользовательским декодером кадров.
     *
     * @param deviceId идентификатор физического устройства
     * @param transport транспорт этого устройства
     * @param configuration ограничения очередей и буферов
     * @param frameDecoder декодер транспортных кадров
     */
    public PeripheralDeviceWorker(
            String deviceId,
            PeripheralTransport transport,
            PeripheralWorkerConfiguration configuration,
            PeripheralFrameDecoder frameDecoder
    ) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("Device id must not be blank");
        }
        this.deviceId = deviceId;
        this.transport = Objects.requireNonNull(transport, "transport");
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration"
        );
        this.frameDecoder = Objects.requireNonNull(
                frameDecoder,
                "frameDecoder"
        );
        receiveBuffer = new ByteRingBuffer(
                configuration.receiveBufferCapacity()
        );
        commandQueue = new PeripheralCommandQueue(
                configuration.commandQueueCapacity()
        );
        frameQueue = new LinkedBlockingQueue<>(
                configuration.frameQueueCapacity()
        );
    }

    /**
     * Открывает транспорт и запускает отдельные RX/TX-потоки.
     *
     * <p>Перед запуском очищаются очереди и, если это задано конфигурацией,
     * старые входящие данные устройства.</p>
     */
    public synchronized void start() {
        if (state.get() != PeripheralWorkerState.STOPPED) {
            log.debug(
                    "Cannot start peripheral worker {} from state {}",
                    deviceId,
                    state.get()
            );
            throw new IllegalStateException(
                    "Peripheral worker " + deviceId + " is already started"
            );
        }

        log.debug("Starting peripheral worker {}", deviceId);
        state.set(PeripheralWorkerState.STARTING);
        lastError.set("");
        receiveBuffer.clear();
        commandQueue.clear();
        frameQueue.clear();

        try {
            // Транспорт открывается до потоков: они никогда не увидят полусеанс.
            transport.open();
            if (configuration.discardInputOnStart()) {
                transport.clearInput();
            }
            running.set(true);
            executor = Executors.newFixedThreadPool(
                    2,
                    new WorkerThreadFactory(deviceId)
            );
            state.set(PeripheralWorkerState.RUNNING);

            // Первый поток блокируется на чтении, второй — на очереди команд.
            executor.submit(this::receiveLoop);
            executor.submit(this::transmitLoop);
            log.info("Peripheral worker {} started", deviceId);
        } catch (RuntimeException exception) {
            // Любая ошибка старта переводит объект в FAILED и закрывает транспорт.
            fail(exception);
            throw exception;
        }
    }

    /**
     * Ставит обычную команду в конец очереди.
     *
     * @param command готовые транспортные байты команды
     */
    public void submit(byte[] command) {
        requireRunning();
        commandQueue.submit(command);
        log.debug(
                "Queued {} bytes for peripheral {}, queue size={}",
                command.length,
                deviceId,
                commandQueue.size()
        );
    }

    /**
     * Ставит аварийную команду перед обычными командами.
     *
     * @param command готовые транспортные байты команды
     */
    public void submitFirst(byte[] command) {
        requireRunning();
        commandQueue.submitFirst(command);
        log.debug(
                "Queued {} priority bytes for peripheral {}, queue size={}",
                command.length,
                deviceId,
                commandQueue.size()
        );
    }

    /**
     * Ожидает ближайший полностью собранный входящий кадр.
     *
     * @param timeout максимальное время ожидания
     * @return кадр либо пустой результат при тайм-ауте
     */
    public Optional<byte[]> awaitFrame(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        requireRunning();
        try {
            byte[] frame = frameQueue.poll(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (frame == null) {
                log.debug(
                        "Timed out waiting {} ms for frame from peripheral {}",
                        timeout.toMillis(),
                        deviceId
                );
            } else {
                log.debug(
                        "Received frame from peripheral {}, bytes={}",
                        deviceId,
                        frame.length
                );
            }
            return frame == null
                    ? Optional.empty()
                    : Optional.of(Arrays.copyOf(frame, frame.length));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.debug(
                    "Interrupted while waiting for frame from peripheral {}",
                    deviceId
            );
            throw new PeripheralCommunicationException(
                    "Interrupted while waiting for data from " + deviceId,
                    exception
            );
        }
    }

    /**
     * Последовательно выполняет один обмен «команда → ответ».
     *
     * <p>{@code exchangeLock} гарантирует, что два вызывающих потока не заберут
     * ответы друг друга. Сопоставление по идентификатору протокола может быть
     * добавлено конкретным адаптером поверх этого базового механизма.</p>
     *
     * @param command команда с уже добавленными протокольными маркерами
     * @param timeout время ожидания ответа
     * @return первый полученный после отправки кадр
     */
    public byte[] exchange(byte[] command, Duration timeout) {
        log.debug(
                "Starting request/response exchange with peripheral {}, command bytes={}",
                deviceId,
                command == null ? 0 : command.length
        );
        exchangeLock.lock();
        try {
            submit(command);
            return awaitFrame(timeout).orElseThrow(
                    () -> new PeripheralCommunicationException(
                            "Response timed out for " + deviceId
                    )
            );
        } finally {
            exchangeLock.unlock();
        }
    }

    /**
     * Формирует неизменяемый диагностический снимок без остановки потоков.
     *
     * @return актуальные состояния очередей, счётчики и последняя ошибка
     */
    public PeripheralWorkerSnapshot snapshot() {
        return new PeripheralWorkerSnapshot(
                deviceId,
                state.get(),
                commandQueue.size(),
                receiveBuffer.size(),
                frameQueue.size(),
                bytesReceived.get(),
                bytesSent.get(),
                framesReceived.get(),
                framesDropped.get(),
                lastReceiveEpochMillis.get(),
                lastTransmitEpochMillis.get(),
                lastError.get()
        );
    }

    /**
     * Штатно останавливает RX/TX, закрывает транспорт и очищает очереди.
     *
     * <p>Вместо небезопасного {@code Thread.stop()} потоки получают interrupt,
     * а закрытие транспорта разблокирует ожидающее чтение.</p>
     */
    public synchronized void stop() {
        PeripheralWorkerState current = state.get();
        if (current == PeripheralWorkerState.STOPPED) {
            log.debug("Peripheral worker {} is already stopped", deviceId);
            return;
        }
        boolean wasActive = current == PeripheralWorkerState.RUNNING
                || current == PeripheralWorkerState.STARTING;
        if (wasActive) {
            log.info(
                    "Stopping peripheral worker {} from state {}",
                    deviceId,
                    current
            );
        } else {
            log.debug(
                    "Resetting peripheral worker {} from state {}",
                    deviceId,
                    current
            );
        }
        state.set(PeripheralWorkerState.STOPPING);
        running.set(false);
        if (executor != null) {
            // take()/poll()/чтение получают сигнал остановки.
            executor.shutdownNow();
        }
        transport.close();
        if (executor != null) {
            try {
                // Не блокируем завершение приложения дольше заданного лимита.
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    lastError.compareAndSet(
                            "",
                            "Peripheral worker did not stop within 2 seconds"
                    );
                    log.warn(
                            "Peripheral worker {} did not stop within 2 seconds",
                            deviceId
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                lastError.compareAndSet(
                        "",
                        "Interrupted while stopping peripheral worker"
                );
                log.warn(
                        "Interrupted while stopping peripheral worker {}",
                        deviceId
                );
            }
        }
        commandQueue.clear();
        receiveBuffer.clear();
        state.set(PeripheralWorkerState.STOPPED);
        if (wasActive) {
            log.info(
                    "Peripheral worker {} stopped: receivedBytes={}, sentBytes={}, receivedFrames={}, droppedFrames={}",
                    deviceId,
                    bytesReceived.get(),
                    bytesSent.get(),
                    framesReceived.get(),
                    framesDropped.get()
            );
        } else {
            log.debug("Peripheral worker {} reset to STOPPED", deviceId);
        }
    }

    /** Делегирует закрытие штатной остановке worker. */
    @Override
    public void close() {
        stop();
    }

    /**
     * RX-цикл: читает порции, дописывает кольцо и публикует готовые кадры.
     */
    private void receiveLoop() {
        log.debug("RX loop started for peripheral {}", deviceId);
        byte[] chunk = new byte[configuration.readChunkSize()];
        while (running.get()) {
            try {
                int read = transport.read(chunk);
                if (read < 0) {
                    if (!running.get()) {
                        return;
                    }
                    throw new PeripheralCommunicationException(
                            "Peripheral " + deviceId
                                    + " closed the input stream"
                    );
                }
                if (read == 0) {
                    continue;
                }
                bytesReceived.addAndGet(read);
                lastReceiveEpochMillis.set(System.currentTimeMillis());
                receiveBuffer.write(chunk, read);
                log.trace(
                        "RX peripheral {}: bytes={}, bufferedBytes={}",
                        deviceId,
                        read,
                        receiveBuffer.size()
                );

                byte[] frame;
                while ((frame = frameDecoder.pollFrame(receiveBuffer)) != null) {
                    framesReceived.incrementAndGet();
                    // При заполнении сохраняем самое новое состояние устройства.
                    if (!frameQueue.offer(frame)) {
                        frameQueue.poll();
                        frameQueue.offer(frame);
                        long dropped = framesDropped.incrementAndGet();
                        // Preserve visibility of data loss without writing one
                        // warning for every frame in a sustained overload.
                        if (dropped == 1 || dropped % 1000 == 0) {
                            log.warn(
                                    "Frame queue full for peripheral {}; oldest frame dropped, totalDropped={}",
                                    deviceId,
                                    dropped
                            );
                        }
                    }
                }
            } catch (RuntimeException exception) {
                if (running.get()) {
                    fail(exception);
                }
                return;
            }
        }
        log.debug("RX loop stopped for peripheral {}", deviceId);
    }

    /**
     * TX-цикл: блокируется на очереди и последовательно отправляет команды.
     */
    private void transmitLoop() {
        log.debug("TX loop started for peripheral {}", deviceId);
        while (running.get()) {
            try {
                byte[] command = commandQueue.take();
                transport.write(command);
                bytesSent.addAndGet(command.length);
                lastTransmitEpochMillis.set(System.currentTimeMillis());
                log.trace(
                        "TX peripheral {}: bytes={}",
                        deviceId,
                        command.length
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.debug("TX loop interrupted for peripheral {}", deviceId);
                return;
            } catch (RuntimeException exception) {
                if (running.get()) {
                    fail(exception);
                }
                return;
            }
        }
        log.debug("TX loop stopped for peripheral {}", deviceId);
    }

    /**
     * Фиксирует ошибку, останавливает оба потока и закрывает транспорт.
     *
     * @param exception ошибка транспорта, буфера или декодера
     */
    private void fail(RuntimeException exception) {
        lastError.set(
                exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage()
        );
        state.set(PeripheralWorkerState.FAILED);
        running.set(false);
        // Scheduled device services record the first outage and recovery.
        // Keep the repeated transport stack trace available only in DEBUG.
        log.debug(
                "Peripheral worker {} failed: {}",
                deviceId,
                lastError.get(),
                exception
        );
        ExecutorService currentExecutor = executor;
        if (currentExecutor != null) {
            currentExecutor.shutdownNow();
        }
        transport.close();
    }

    /** Проверяет, что публичная операция вызывается в рабочем состоянии. */
    private void requireRunning() {
        if (state.get() != PeripheralWorkerState.RUNNING) {
            log.debug(
                    "Peripheral operation rejected for {} in state {}",
                    deviceId,
                    state.get()
            );
            throw new IllegalStateException(
                    "Peripheral worker " + deviceId + " is not running"
            );
        }
    }

    /**
     * Создаёт daemon-потоки с диагностическими именами устройства и роли.
     */
    private static final class WorkerThreadFactory implements ThreadFactory {

        private final String deviceId;
        private final AtomicInteger counter = new AtomicInteger();

        /**
         * @param deviceId идентификатор устройства для имени потока
         */
        private WorkerThreadFactory(String deviceId) {
            this.deviceId = deviceId;
        }

        /**
         * @param task цикл приёма или передачи
         * @return новый именованный daemon-поток
         */
        @Override
        public Thread newThread(Runnable task) {
            int index = counter.incrementAndGet();
            String role = index == 1 ? "rx" : "tx";
            Thread thread = new Thread(
                    task,
                    "peripheral-" + deviceId + "-" + role
            );
            thread.setDaemon(true);
            return thread;
        }
    }
}
