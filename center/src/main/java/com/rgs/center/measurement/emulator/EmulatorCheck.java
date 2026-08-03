package com.rgs.center.measurement.emulator;

/** Один эмулируемый контрольный признак готовности. */
public record EmulatorCheck(String code, String label, boolean ready, String details) {
}
