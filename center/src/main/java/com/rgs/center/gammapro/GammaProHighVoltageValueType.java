package com.rgs.center.gammapro;

/**
 * Тип значения команды {@code SET_HV}, определённый главой 17 руководства
 * Gamma-PRO.
 */
public enum GammaProHighVoltageValueType {

    /** Непосредственно высокое напряжение, {@code Type_value=0}. */
    HIGH_VOLTAGE(0),

    /** Управляющее напряжение анализатора, {@code Type_value=1}. */
    CONTROL_VOLTAGE(1);

    private final int protocolCode;

    GammaProHighVoltageValueType(int protocolCode) {
        this.protocolCode = protocolCode;
    }

    /** @return числовое значение {@code Type_value} в протоколе Gamma-PRO */
    public int protocolCode() {
        return protocolCode;
    }
}
