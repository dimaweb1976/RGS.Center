package com.rgs.center.manipulator;

/** Положение люка по двум дискретным концевым выключателям. */
public enum SpectrometerHatchStatus {
    CLOSED("Closed"),
    OPEN("Open"),
    INTERMEDIATE("Intermediate"),
    INVALID("Invalid"),
    UNKNOWN("Unknown");

    private final String label;

    SpectrometerHatchStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
