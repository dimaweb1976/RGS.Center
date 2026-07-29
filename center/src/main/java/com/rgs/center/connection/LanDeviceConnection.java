package com.rgs.center.connection;

import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Очередной LAN-транспорт для проверки доступности сетевого устройства.
 *
 * <p>Команда {@code PING} поступает через общий TX-worker. Результат проверки
 * публикуется как строковый кадр {@code ONLINE} или {@code OFFLINE}, который
 * забирает общий RX-worker. Поэтому сетевые и COM-устройства обслуживаются
 * одинаковой парой очередей чтения/записи.</p>
 */
public final class LanDeviceConnection extends AbstractDeviceConnection {

    private static final Logger log =
            LoggerFactory.getLogger(LanDeviceConnection.class);

    public static final byte[] PING_COMMAND =
            "PING\n".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] ONLINE_RESPONSE =
            "ONLINE\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OFFLINE_RESPONSE =
            "OFFLINE\n".getBytes(StandardCharsets.US_ASCII);

    /** Настройки читаются при каждом запросе и могут меняться во время работы. */
    private final Supplier<String> addressSupplier;
    private final IntSupplier timeoutSupplier;

    /** Конкретная системная реализация reachability. */
    private final LanReachabilityProbe reachabilityProbe;

    /** Мост между TX-проверкой и блокирующим чтением RX-worker. */
    private final LinkedBlockingQueue<byte[]> responseQueue =
            new LinkedBlockingQueue<>(16);

    /**
     * @param connectionId диагностическое имя
     * @param addressSupplier источник актуального IP-адреса
     * @param timeoutSupplier источник актуального тайм-аута
     * @param reachabilityProbe реализация проверки LAN
     */
    public LanDeviceConnection(
            String connectionId,
            Supplier<String> addressSupplier,
            IntSupplier timeoutSupplier,
            LanReachabilityProbe reachabilityProbe
    ) {
        super(connectionId);
        this.addressSupplier = Objects.requireNonNull(addressSupplier);
        this.timeoutSupplier = Objects.requireNonNull(timeoutSupplier);
        this.reachabilityProbe = Objects.requireNonNull(reachabilityProbe);
    }

    /**
     * Инициализирует логический LAN-канал. Фактическая проверка устройства
     * выполняется только командой из TX-очереди.
     */
    @Override
    protected void doOpen() {
        responseQueue.clear();
        log.debug("LAN channel {} initialized", connectionId());
    }

    /** Очищает ответы, ожидающие обработки. */
    @Override
    protected void doClose() {
        responseQueue.clear();
    }

    /**
     * Блокирующе читает очередной результат сетевой команды.
     */
    @Override
    public int read(byte[] buffer) {
        requireOpen();
        try {
            byte[] response = responseQueue.poll(
                    Math.max(1, timeoutSupplier.getAsInt()),
                    TimeUnit.MILLISECONDS
            );
            if (response == null) {
                return 0;
            }
            int length = Math.min(buffer.length, response.length);
            System.arraycopy(response, 0, buffer, 0, length);
            return length;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /**
     * Выполняет команду LAN в TX-потоке и ставит ответ в RX-очередь.
     */
    @Override
    public void write(byte[] value) {
        requireOpen();
        if (!Arrays.equals(PING_COMMAND, value)) {
            throw new PeripheralCommunicationException(
                    "Unsupported LAN command for " + connectionId()
            );
        }

        String address = addressSupplier.get();
        int timeoutMillis = Math.max(1, timeoutSupplier.getAsInt());
        boolean reachable = address != null
                && !address.isBlank()
                && reachabilityProbe.isReachable(address, timeoutMillis);
        byte[] response = reachable
                ? ONLINE_RESPONSE
                : OFFLINE_RESPONSE;

        if (!responseQueue.offer(Arrays.copyOf(response, response.length))) {
            responseQueue.poll();
            responseQueue.offer(Arrays.copyOf(response, response.length));
            log.warn(
                    "LAN response queue overflow for {}; oldest result dropped",
                    connectionId()
            );
        }
    }

    /** Удаляет необработанные сетевые ответы. */
    @Override
    public void clearInput() {
        requireOpen();
        responseQueue.clear();
    }

    /**
     * @param response строковый кадр общего worker
     * @return является ли кадр успешным результатом ping
     */
    public static boolean isOnlineResponse(byte[] response) {
        return "ONLINE".equals(
                new String(response, StandardCharsets.US_ASCII).trim()
        );
    }
}
