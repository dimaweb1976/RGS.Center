package com.rgs.center.service;

import com.rgs.center.connection.LanDeviceConnection;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;

import java.time.Duration;

/**
 * Базовый сервис сетевого устройства с очередным LAN-обменом.
 */
public abstract class AbstractNetworkDeviceService
        extends AbstractQueuedDeviceService {

    /** Преобразует витрину в web-модель устройства. */
    private final DeviceViewService deviceViewService;

    /** Общий сценарий фиксации ping в витрине. */
    private final DevicePingService pingService;

    /**
     * @param deviceCode код конкретного сетевого устройства
     * @param deviceViewService витрина для web-интерфейса
     * @param pingService настройки, reachability и фиксация результата
     */
    protected AbstractNetworkDeviceService(
            DeviceCode deviceCode,
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        super(
                deviceCode,
                pingService.createLanConnection(deviceCode),
                PeripheralWorkerConfiguration.lineDevice(32)
        );
        this.deviceViewService = deviceViewService;
        this.pingService = pingService;
    }

    /**
     * Выполняет ping через общие TX/RX-очереди и обновляет витрину.
     *
     * @return результат проверки доступности
     */
    protected final boolean pollQueued() {
        if (!isEnabled()) {
            return false;
        }
        return pingService.executePing(
                deviceCode(),
                () -> {
                    byte[] response = exchangeQueued(
                            LanDeviceConnection.PING_COMMAND,
                            Duration.ofMillis(
                                    pingService.timeoutMillis() + 1000L
                            )
                    );
                    return LanDeviceConnection.isOnlineResponse(response);
                }
        );
    }

    /**
     * @return последний снимок устройства для пользовательского интерфейса
     */
    protected final Device currentDevice() {
        return deviceViewService.getDevice(deviceCode());
    }
}
