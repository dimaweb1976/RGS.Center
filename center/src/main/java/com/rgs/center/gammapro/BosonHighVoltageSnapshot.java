package com.rgs.center.gammapro;

/**
 * Последняя подтверждённая Gamma-PRO команда управления HV на BOSON.
 *
 * <p>Ответ {@code SET_HV=OK} подтверждает принятие команды программой
 * Gamma-PRO, но не является измерением фактического напряжения BOSON.</p>
 *
 * @param mcaName выбранный анализатор
 * @param channelName выбранный канал
 * @param requestedEnabled затребовано ли включение HV
 * @param requestedValue переданное целевое значение
 * @param valueType тип целевого значения
 * @param commandAccepted подтверждена ли команда ответом SET_HV=OK
 * @param commandedAtEpochSeconds время подтверждения команды
 * @param lastError последняя ошибка команды
 */
public record BosonHighVoltageSnapshot(
        String mcaName,
        String channelName,
        boolean requestedEnabled,
        int requestedValue,
        GammaProHighVoltageValueType valueType,
        boolean commandAccepted,
        long commandedAtEpochSeconds,
        String lastError
) {
    /** Начальное состояние до первой команды. */
    public static BosonHighVoltageSnapshot initial() {
        return new BosonHighVoltageSnapshot(
                "",
                "",
                false,
                0,
                GammaProHighVoltageValueType.HIGH_VOLTAGE,
                false,
                0L,
                ""
        );
    }
}
