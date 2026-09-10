package com.rgs.center.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Запускает подключения всех зарегистрированных устройств вместе с приложением.
 *
 * <p>Spring автоматически передаёт сюда каждый сервис, реализующий
 * {@link ManagedDeviceConnection}. Для нового устройства достаточно создать
 * bean-наследник общего базового класса — менеджер подхватит его без изменения
 * собственного кода.</p>
 */
@Component
public class DeviceConnectionManager implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(DeviceConnectionManager.class);

    /** Стабильно отсортированный набор подключаемых устройств. */
    private final List<ManagedDeviceConnection> devices;

    /** Состояние менеджера для контракта SmartLifecycle. */
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * @param devices все управляемые подключения из Spring-контекста
     */
    public DeviceConnectionManager(List<ManagedDeviceConnection> devices) {
        this.devices = devices.stream()
                .sorted(Comparator.comparing(
                        device -> device.deviceCode().getCode()
                ))
                .toList();
    }

    /**
     * Последовательно пытается открыть связь со всеми устройствами.
     *
     * <p>Ошибка одного физического устройства не блокирует запуск остальных
     * и самого web-приложения.</p>
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        int started = 0;
        int enabled = 0;
        for (ManagedDeviceConnection device : devices) {
            if (!device.isEnabled()) {
                log.info(
                        "Device {} is disabled by configuration ({})",
                        device.deviceCode(),
                        device.deviceCode().getEnabledProperty()
                );
                continue;
            }
            enabled++;
            try {
                device.startCommunication();
                started++;
            } catch (RuntimeException exception) {
                log.error(
                        "Initial connection failed for device {}",
                        device.deviceCode(),
                        exception
                );
            }
        }
        log.info(
                "Initial device connections completed: started={}, enabled={}, total={}",
                started,
                enabled,
                devices.size()
        );
    }

    /** Закрывает подключения в обратном порядке регистрации. */
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (int index = devices.size() - 1; index >= 0; index--) {
            ManagedDeviceConnection device = devices.get(index);
            if (!device.isEnabled()) {
                continue;
            }
            try {
                device.stopCommunication();
            } catch (RuntimeException exception) {
                log.warn(
                        "Cannot stop connection for device {}",
                        device.deviceCode(),
                        exception
                );
            }
        }
        log.info("All managed device connections stopped");
    }

    /** @return запущен ли менеджер */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Stops and reopens one device so transports backed by SettingsService read
     * the values most recently saved in PostgreSQL.
     */
    public synchronized DeviceReconfigurationResult reconfigure(String deviceCode) {
        ManagedDeviceConnection device = devices.stream()
                .filter(candidate -> candidate.deviceCode().getCode().equals(deviceCode))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Unknown managed device: " + deviceCode
                ));

        try {
            device.stopCommunication();
            if (!device.isEnabled()) {
                return new DeviceReconfigurationResult(
                        deviceCode,
                        true,
                        "DISABLED",
                        "Settings saved; device is disabled by application configuration"
                );
            }
            device.startCommunication();
            return new DeviceReconfigurationResult(
                    deviceCode,
                    true,
                    device.communicationSnapshot().state().name(),
                    "Connection settings applied"
            );
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.error("Cannot apply connection settings for {}", deviceCode, exception);
            return new DeviceReconfigurationResult(
                    deviceCode,
                    false,
                    "FAILED",
                    message
            );
        }
    }

    /** Подключения должны открываться автоматически при запуске Spring. */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * Запускает менеджер до поздних прикладных lifecycle-компонентов.
     */
    @Override
    public int getPhase() {
        return 0;
    }
}
