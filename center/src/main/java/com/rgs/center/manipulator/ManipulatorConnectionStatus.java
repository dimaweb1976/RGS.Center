package com.rgs.center.manipulator;

/** Состояние двух TCP-каналов и актуальности телеметрии Dobot. */
public enum ManipulatorConnectionStatus {
    DISABLED("Disabled", "manipulator-connection--disabled"),
    CONNECTING("Connecting", "manipulator-connection--connecting"),
    ONLINE("Online", "manipulator-connection--online"),
    OFFLINE("Offline", "manipulator-connection--offline");

    private final String label;
    private final String cssClass;

    ManipulatorConnectionStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String getLabel() {
        return label;
    }

    public String getCssClass() {
        return cssClass;
    }
}
