package com.rgs.center.service;

import com.rgs.center.connection.LanDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Общий сценарий ping сетевого устройства с обновлением витрины.
 */
@Component
public class DevicePingService {

    private static final Logger log =
            LoggerFactory.getLogger(DevicePingService.class);

    private final SettingsService settingsService;
    private final CurrentDataService currentDataService;
    private final ReachabilityChecker reachabilityChecker;

    /**
     * @param settingsService источник адреса и тайм-аута
     * @param currentDataService витрина результата
     * @param reachabilityChecker реализация сетевой проверки
     */
    public DevicePingService(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            ReachabilityChecker reachabilityChecker
    ) {
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
        this.reachabilityChecker = reachabilityChecker;
    }

    /**
     * Совместимый синхронный вход для прямой проверки доступности.
     *
     * <p>Сервисы устройств используют {@link #executePing(DeviceCode,
     * BooleanSupplier)} и передают туда обмен через очереди. Этот метод
     * сохранён для низкоуровневых тестов и служебных вызовов.</p>
     *
     * @param device код сетевого устройства
     * @return результат ping
     */
    public boolean pingDevice(DeviceCode device) {
        return executePing(
                device,
                () -> reachabilityChecker.isReachable(
                        settingsService.getValue(
                                device.getIpSettingCode(),
                                String.class
                        ),
                        timeoutMillis()
                )
        );
    }

    /**
     * Обрамляет транспортную ping-команду единым обновлением витрины.
     *
     * @param device код сетевого устройства
     * @param pingOperation операция, выполняемая через очередь устройства
     * @return результат проверки
     */
    public boolean executePing(
            DeviceCode device,
            BooleanSupplier pingOperation
    ) {
        String address = settingsService.getValue(
                device.getIpSettingCode(),
                String.class
        );
        int timeoutMillis = Math.toIntExact(settingsService.getValue(
                SettingsService.PING_TIMEOUT_MILLIS,
                Long.class
        ));

        boolean previousPinged = currentDataService.getValue(
                device,
                CurrentDataService.PINGED,
                Boolean.class
        );
        long previousCheck = currentDataService.getValue(
                device,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        );
        long sentAt = Instant.now().getEpochSecond();
        // Время входящего взаимодействия фиксируется до сетевого вызова.
        currentDataService.setValue(
                device,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                sentAt
        );

        long startedAt = System.nanoTime();
        if (address.isBlank()) {
            if (previousCheck == 0) {
                log.warn("IP address is not configured for device {}", device);
            } else {
                log.debug("IP address is not configured for device {}", device);
            }
        } else {
            log.debug(
                    "Checking reachability of device {} at {}, timeout={} ms",
                    device,
                    address,
                    timeoutMillis
            );
        }
        boolean pinged = !address.isBlank()
                && pingOperation.getAsBoolean();
        long durationMillis =
                (System.nanoTime() - startedAt) / 1_000_000L;
        currentDataService.setValue(
                device,
                CurrentDataService.PINGED,
                pinged
        );

        if (previousCheck == 0 || previousPinged != pinged) {
            log.info(
                    "Device {} at {} is {} (check took {} ms)",
                    device,
                    address.isBlank() ? "<not configured>" : address,
                    pinged ? "ONLINE" : "OFFLINE",
                    durationMillis
            );
        } else {
            log.debug(
                    "Device {} remains {} (check took {} ms)",
                    device,
                    pinged ? "ONLINE" : "OFFLINE",
                    durationMillis
            );
        }

        if (pinged) {
            // Время ответа изменяется только после успешной проверки.
            currentDataService.setValue(
                    device,
                    CurrentDataService.INTERACTION_LASTDATE_OUT,
                    Instant.now().getEpochSecond()
            );
        }

        return pinged;
    }

    /**
     * Создаёт LAN-подключение, читающее актуальные настройки при каждом ping.
     *
     * @param device код сетевого устройства
     * @return транспорт для общего RX/TX-worker
     */
    public LanDeviceConnection createLanConnection(DeviceCode device) {
        return new LanDeviceConnection(
                "lan:" + device.getCode(),
                () -> settingsService.getValue(
                        device.getIpSettingCode(),
                        String.class
                ),
                this::timeoutMillis,
                reachabilityChecker::isReachable
        );
    }

    /**
     * @return текущий тайм-аут проверки LAN
     */
    public int timeoutMillis() {
        return Math.toIntExact(settingsService.getValue(
                SettingsService.PING_TIMEOUT_MILLIS,
                Long.class
        ));
    }
}
