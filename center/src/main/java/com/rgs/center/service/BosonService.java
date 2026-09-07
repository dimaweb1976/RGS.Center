package com.rgs.center.service;

import com.rgs.center.gammapro.BosonHighVoltageSnapshot;
import com.rgs.center.gammapro.GammaProCommandResult;
import com.rgs.center.gammapro.GammaProHighVoltageControl;
import com.rgs.center.gammapro.GammaProHighVoltageValueType;
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

    /** Значение HV из утверждённых ручных команд страницы GammaPro_BOSON. */
    public static final int MANUAL_HIGH_VOLTAGE = 1000;

    /** Команды BOSON проходят через TCP-сервер Gamma-PRO. */
    private final GammaProHighVoltageControl highVoltageControl;

    /**
     * Создаёт сервис Boson.
     *
     * @param deviceViewService витрина текущего состояния устройств
     * @param pingService сервис выполнения ping-проверки
     * @param highVoltageControl управление BOSON через Gamma-PRO
     */
    public BosonService(
            DeviceViewService deviceViewService,
            DevicePingService pingService,
            GammaProHighVoltageControl highVoltageControl
    ) {
        super(DeviceCode.BOSON, deviceViewService, pingService);
        this.highVoltageControl = highVoltageControl;
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

    /**
     * Выбирает настроенный тракт BOSON в Gamma-PRO без изменения HV.
     * Ответ SET_TRACT=OK подтверждает только выбор логического тракта.
     */
    public GammaProCommandResult selectTract(
            String mcaName,
            String channelName
    ) {
        return highVoltageControl.selectTract(mcaName, channelName);
    }

    /**
     * Включает HV на BOSON командой, переданной через Gamma-PRO.
     *
     * @param mcaName имя анализатора в Gamma-PRO
     * @param channelName имя спектрометрического канала
     * @param voltage целевое высокое напряжение
     * @return подтверждение принятия команды Gamma-PRO
     */
    public BosonHighVoltageSnapshot enableHighVoltage(
            String mcaName,
            String channelName,
            int voltage
    ) {
        return highVoltageControl.applyBosonHighVoltage(
                mcaName,
                channelName,
                true,
                voltage,
                GammaProHighVoltageValueType.HIGH_VOLTAGE
        );
    }

    /** Выключает HV на BOSON через тот же тракт и очередь Gamma-PRO. */
    public BosonHighVoltageSnapshot disableHighVoltage(
            String mcaName,
            String channelName
    ) {
        return highVoltageControl.applyBosonHighVoltage(
                mcaName,
                channelName,
                false,
                MANUAL_HIGH_VOLTAGE,
                GammaProHighVoltageValueType.HIGH_VOLTAGE
        );
    }
}
