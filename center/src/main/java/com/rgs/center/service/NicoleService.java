package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Самостоятельный сервис контроля доступности устройства Nicole.
 *
 * <p>Плановый опрос Nicole не зависит от времени выполнения опросов камеры,
 * Boson и RoboPLC. Полученный результат публикуется в витрине.</p>
 */
@Service
public class NicoleService extends AbstractNetworkDeviceService {

    /**
     * Создаёт сервис Nicole.
     *
     * @param deviceViewService витрина текущего состояния устройств
     * @param pingService сервис выполнения ping-проверки
     */
    public NicoleService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        super(DeviceCode.NICOLE, deviceViewService, pingService);
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.nicole.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.nicole.poll-initial-delay-millis:0}"
    )
    /**
     * Проверяет доступность Nicole и обновляет запись устройства в витрине.
     */
    public void poll() {
        pollQueued();
    }

    /**
     * Возвращает последнее состояние Nicole из витрины.
     *
     * @return снимок текущего состояния устройства
     */
    public Device getNicole() {
        return currentDevice();
    }
}
