package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Самостоятельный сервис контроля доступности контроллера RoboPLC.
 *
 * <p>Периодический опрос выполняется планировщиком независимо от опросов
 * остальных устройств. Результат сохраняется в общей витрине текущих данных.</p>
 */
@Service
public class RoboPlcService extends AbstractNetworkDeviceService {

    /**
     * Создаёт сервис RoboPLC.
     *
     * @param deviceViewService витрина текущего состояния устройств
     * @param pingService сервис выполнения ping-проверки
     */
    public RoboPlcService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        super(DeviceCode.ROBOPLC, deviceViewService, pingService);
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.roboplc.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.roboplc.poll-initial-delay-millis:0}"
    )
    /**
     * Проверяет доступность RoboPLC.
     *
     * <p>{@code fixedDelay} отсчитывается после завершения предыдущей проверки,
     * поэтому два опроса одного устройства не выполняются одновременно.</p>
     */
    public void poll() {
        pollQueued();
    }

    /**
     * Возвращает последнее состояние RoboPLC из витрины.
     *
     * @return снимок текущего состояния устройства
     */
    public Device getRoboPlc() {
        return currentDevice();
    }
}
