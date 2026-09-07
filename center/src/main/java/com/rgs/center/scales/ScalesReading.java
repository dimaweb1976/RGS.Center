package com.rgs.center.scales;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.math.BigDecimal;

/** One decoded mass frame returned by the scales. */
public record ScalesReading(
        BigDecimal mass,
        String unit,
        ScalesStability stability,
        long receivedAtEpochSeconds
) {
    /** Returns the measurement normalized to grams for the UI and zero check. */
    public BigDecimal massGrams() {
        return switch (unit) {
            case "g" -> mass;
            case "kg" -> mass.multiply(BigDecimal.valueOf(1000));
            case "mg" -> mass.divide(BigDecimal.valueOf(1000));
            default -> throw new PeripheralCommunicationException(
                    "Unsupported scales mass unit: " + unit
            );
        };
    }

    public boolean isZeroGrams() {
        return massGrams().compareTo(BigDecimal.ZERO) == 0;
    }
}
