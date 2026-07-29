package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Самостоятельный сервис контроля доступности камеры машинного зрения.
 *
 * <p>Сейчас сконфигурирована одна камера. Методы сервиса уже отделяют одиночное
 * устройство от списка камер, чтобы позднее расширить конфигурацию без изменения
 * MVC-контроллера.</p>
 */
@Service
public class CameraService extends AbstractNetworkDeviceService {

    /**
     * Создаёт сервис камеры.
     *
     * @param deviceViewService витрина текущего состояния устройств
     * @param pingService сервис выполнения ping-проверки
     */
    public CameraService(
            DeviceViewService deviceViewService,
            DevicePingService pingService
    ) {
        super(DeviceCode.CAMERA, deviceViewService, pingService);
    }

    @Scheduled(
            fixedDelayString = "${rgs.device.camera.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.camera.poll-initial-delay-millis:0}"
    )
    /**
     * Проверяет доступность камеры и обновляет её запись в витрине.
     */
    public void poll() {
        pollQueued();
    }

    /**
     * Возвращает последнее состояние настроенной камеры.
     *
     * @return снимок текущего состояния камеры
     */
    public Device getCamera() {
        return currentDevice();
    }

    /**
     * Возвращает камеры, которые должны отображаться в интерфейсе.
     *
     * <p>На текущем этапе список содержит одну камеру. Этот метод является точкой
     * расширения для будущей поддержки нескольких камер.</p>
     *
     * @return неизменяемый список камер
     */
    public List<Device> getCameras() {
        return List.of(getCamera());
    }
}
