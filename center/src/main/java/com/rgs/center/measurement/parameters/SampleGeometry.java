package com.rgs.center.measurement.parameters;

/** Поддерживаемая геометрия образца для настройки измерения. */
public enum SampleGeometry {
    BOTTLE("Bottle"),
    VIAL("Vial"),
    MARINELLI("Marinelli beaker");

    private final String label;

    SampleGeometry(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
