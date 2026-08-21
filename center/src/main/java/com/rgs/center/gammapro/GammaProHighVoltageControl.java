package com.rgs.center.gammapro;

/**
 * Порт управления BOSON через единственное TCP-соединение Gamma-PRO.
 */
public interface GammaProHighVoltageControl {

    /**
     * Выбирает спектрометрический тракт и передаёт ему команду SET_HV.
     *
     * @param mcaName имя анализатора в Gamma-PRO
     * @param channelName имя канала в Gamma-PRO
     * @param enabled требуемое состояние высокого напряжения
     * @param value целевое значение
     * @param valueType тип значения по протоколу Gamma-PRO
     * @return подтверждённое сервером состояние отправленной команды
     */
    BosonHighVoltageSnapshot applyBosonHighVoltage(
            String mcaName,
            String channelName,
            boolean enabled,
            int value,
            GammaProHighVoltageValueType valueType
    );
}
