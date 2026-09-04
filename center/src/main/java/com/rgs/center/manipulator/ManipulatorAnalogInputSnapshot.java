package com.rgs.center.manipulator;

/** Последнее значение аналогового входа люка, полученное командой AI(index). */
public record ManipulatorAnalogInputSnapshot(
        boolean enabled,
        int index,
        Double value,
        long receivedAtEpochMillis,
        String lastError
) {
}
