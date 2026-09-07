package com.rgs.center.gammapro;

/**
 * Порт управления BOSON через единственное TCP-соединение Gamma-PRO.
 */
public interface GammaProHighVoltageControl {

    /**
     * Выбирает тракт, через который последующие команды Gamma-PRO будут
     * адресованы BOSON. Команды HV всё равно повторяют этот выбор, поскольку
     * другой сценарий мог изменить текущий тракт между ручными действиями.
     *
     * @param mcaName имя анализатора в Gamma-PRO
     * @param channelName имя канала в Gamma-PRO
     * @return результат команды, завершённой ответом SET_TRACT=OK
     */
    GammaProCommandResult selectTract(
            String mcaName,
            String channelName
    );

    /**
     * Выбирает спектрометрический тракт и передаёт ему команду SET_HV.
     *
     * @param mcaName имя анализатора в Gamma-PRO
     * @param channelName имя канала в Gamma-PRO
     * @param enabled параметр IsOn: {@code true} передаёт 1, {@code false} — 0
     * @param value параметр Value_HV — запрошенное целевое значение
     * @param valueType параметр Type_value по протоколу Gamma-PRO
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
