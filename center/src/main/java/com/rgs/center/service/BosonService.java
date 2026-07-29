package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Самостоятельный сервис контроля доступности устройства Boson.
 *
 * <p>Boson не рассматривается как камера: он имеет собственные настройки,
 * расписание опроса и отдельную запись в витрине текущих данных.</p>
 */
@Service
public class BosonService extends AbstractNetworkDeviceService {

    /**
     * Создаёт сервис Boson.
     *
     * @param deviceViewService витрина текущего состояния устройств
     * @param pingService сервис выполнения ping-проверки
     */
    public BosonService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        super(DeviceCode.BOSON, deviceViewService, pingService);
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.boson.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.boson.poll-initial-delay-millis:0}"
    )
    /**
     * Проверяет доступность Boson и обновляет его запись в витрине.
     */
    public void poll() {
        pollQueued();
    }

    /**
     * Возвращает последнее состояние Boson из витрины.
     *
     * @return снимок текущего состояния устройства
     */
    public Device getBoson() {
        return currentDevice();
    }
}
