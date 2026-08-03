package com.rgs.center.connection;

import com.rgs.center.peripheral.PeripheralTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Общая потокобезопасная основа для всех подключений к устройствам.
 *
 * <p>COM-, LAN- и будущие реализации наследуют единый жизненный цикл:
 * идемпотентное открытие, проверку состояния, защиту операций от использования
 * закрытого транспорта и гарантированное освобождение ресурса.</p>
 */
public abstract class AbstractDeviceConnection
        implements PeripheralTransport {

    private static final Logger log =
            LoggerFactory.getLogger(AbstractDeviceConnection.class);

    /** Стабильное имя подключения для журналов и диагностики. */
    private final String connectionId;

    /** Сериализует переходы жизненного цикла open/close. */
    private final ReentrantLock lifecycleLock = new ReentrantLock();

    /** Логическое состояние подключения, видимое рабочим потокам. */
    private volatile boolean open;

    /**
     * @param connectionId уникальное диагностическое имя подключения
     */
    protected AbstractDeviceConnection(String connectionId) {
        this.connectionId = Objects.requireNonNull(
                connectionId,
                "Connection id must not be null"
        );
        if (connectionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Connection id must not be blank"
            );
        }
    }

    /**
     * Открывает конкретный транспорт ровно один раз.
     */
    @Override
    public final void open() {
        lifecycleLock.lock();
        try {
            if (open) {
                log.debug("Connection {} is already open", connectionId);
                return;
            }
            log.debug("Opening device connection {}", connectionId);
            doOpen();
            open = true;
            log.info("Device connection {} opened", connectionId);
        } catch (RuntimeException exception) {
            try {
                doClose();
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            open = false;
            // The owning service decides whether this failure is a first
            // outage, a repeated retry or a terminal error.
            log.debug(
                    "Cannot open device connection {}",
                    connectionId,
                    exception
            );
            throw exception;
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * @return открыто ли подключение с точки зрения общего жизненного цикла
     */
    @Override
    public final boolean isOpen() {
        return open;
    }

    /**
     * Закрывает конкретный транспорт. Повторный вызов безопасен.
     */
    @Override
    public final void close() {
        lifecycleLock.lock();
        try {
            if (!open) {
                return;
            }
            try {
                doClose();
            } finally {
                open = false;
            }
            log.info("Device connection {} closed", connectionId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Device connection {} closed with an error",
                    connectionId,
                    exception
            );
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Проверяет состояние перед чтением, записью или очисткой буфера.
     */
    protected final void requireOpen() {
        if (!open) {
            throw new IllegalStateException(
                    "Device connection " + connectionId + " is not open"
            );
        }
    }

    /**
     * @return стабильное диагностическое имя подключения
     */
    public final String connectionId() {
        return connectionId;
    }

    /**
     * Захватывает системный ресурс конкретного транспорта.
     */
    protected abstract void doOpen();

    /**
     * Освобождает системный ресурс конкретного транспорта.
     */
    protected abstract void doClose();
}
