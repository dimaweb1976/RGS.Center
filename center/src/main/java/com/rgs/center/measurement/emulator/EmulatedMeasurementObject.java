package com.rgs.center.measurement.emulator;

/** Объект, найденный камерой и добавленный в очередь обработки. */
public record EmulatedMeasurementObject(
        int number,
        String barcode,
        double estimatedMassGrams,
        String status
) {
}
