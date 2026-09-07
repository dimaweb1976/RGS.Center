package com.rgs.center.scales;

import com.rgs.center.peripheral.PeripheralCommunicationException;

/** Stability marker returned by the RADWAG character protocol. */
public enum ScalesStability {
    STABLE(' ', "Стабильно"),
    UNSTABLE('?', "Нестабильно"),
    ABOVE_RANGE('^', "Выше диапазона"),
    BELOW_RANGE('v', "Ниже диапазона");

    private final char marker;
    private final String label;

    ScalesStability(char marker, String label) {
        this.marker = marker;
        this.label = label;
    }

    public char marker() {
        return marker;
    }

    public String label() {
        return label;
    }

    /** Converts the one-character protocol marker to a typed value. */
    public static ScalesStability fromMarker(char marker) {
        for (ScalesStability value : values()) {
            if (value.marker == marker) {
                return value;
            }
        }
        throw new PeripheralCommunicationException(
                "Unknown scales stability marker: " + marker
        );
    }
}
