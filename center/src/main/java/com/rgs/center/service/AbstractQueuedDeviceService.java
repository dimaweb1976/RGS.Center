package com.rgs.center.service;

import com.rgs.center.connection.ManagedDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralDeviceWorker;
import com.rgs.center.peripheral.PeripheralTransport;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Базовый класс устройства с едиными очередями команд и входящих кадров.
 *
 * <p>Каждый наследник владеет одним подключением и одним
 * {@link PeripheralDeviceWorker}. Worker создаёт отдельные RX/TX-потоки,
 * поэтому добавление нового устройства не требует новой реализации очередей,
 * остановки потоков или диагностических счётчиков.</p>
 */
public abstract class AbstractQueuedDeviceService
        implements ManagedDeviceConnection {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractQueuedDeviceService.class);

    /** Постоянный код физического устройства. */
    private final DeviceCode deviceCode;

    /** Унифицированный обслуживающий worker подключения. */
    private final PeripheralDeviceWorker worker;

    /** Не допускает одновременный запуск/перезапуск одного worker. */
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    /**
     * @param deviceCode код устройства
     * @param transport COM-, LAN- или другой транспорт
     * @param configuration размеры очередей и буферов
     */
    protected AbstractQueuedDeviceService(
            DeviceCode deviceCode,
            PeripheralTransport transport,
            PeripheralWorkerConfiguration configuration
    ) {
        this.deviceCode = deviceCode;
        worker = new PeripheralDeviceWorker(
                deviceCode.getCode(),
                transport,
                configuration
        );
    }

    /** @return постоянный код устройства */
    @Override
    public final DeviceCode deviceCode() {
        return deviceCode;
    }

    /**
     * Запускает подключение. После транспортной ошибки предварительно
     * переводит worker из FAILED в STOPPED и повторяет открытие.
     */
    @Override
    public final void startCommunication() {
        lifecycleLock.lock();
        try {
            PeripheralWorkerState state =
                    worker.snapshot().state();
            if (state == PeripheralWorkerState.RUNNING) {
                return;
            }
            if (state != PeripheralWorkerState.STOPPED) {
                worker.stop();
            }
            worker.start();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** Останавливает очереди, потоки и транспорт устройства. */
    @Override
    public final void stopCommunication() {
        lifecycleLock.lock();
        try {
            worker.stop();
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** @return снимок состояния подключения и очередей */
    @Override
    public final PeripheralWorkerSnapshot communicationSnapshot() {
        return worker.snapshot();
    }

    /**
     * Ставит команду в общую TX-очередь устройства.
     *
     * @param command готовый транспортный кадр
     */
    protected final void submitQueued(byte[] command) {
        ensureCommunication();
        worker.submit(command);
    }

    /**
     * Выполняет последовательный обмен через TX- и RX-очереди.
     *
     * @param command команда устройства
     * @param timeout максимальное ожидание ответа
     * @return ответный кадр
     */
    protected final byte[] exchangeQueued(
            byte[] command,
            Duration timeout
    ) {
        ensureCommunication();
        return worker.exchange(command, timeout);
    }

    /**
     * Ожидает кадр пассивного устройства, например сканера.
     *
     * @param timeout максимальное ожидание
     * @return входящий кадр либо пустой результат
     */
    protected final Optional<byte[]> awaitQueuedFrame(Duration timeout) {
        ensureCommunication();
        return worker.awaitFrame(timeout);
    }

    /**
     * Гарантирует готовность worker перед прикладной операцией.
     */
    private void ensureCommunication() {
        if (worker.snapshot().state() != PeripheralWorkerState.RUNNING) {
            log.info(
                    "Communication with {} is not running; reconnecting",
                    deviceCode
            );
            startCommunication();
        }
    }
}
