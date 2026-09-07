package com.rgs.center.manipulator;

/** Техническая готовность манипулятора к выполнению следующей команды. */
public enum ManipulatorReadinessStatus {
    READY("Ready", "manipulator-status--ready"),
    BUSY("Busy", "manipulator-status--busy"),
    ALERT("Alert", "manipulator-status--alert"),
    NO_DATA("No data", "manipulator-status--no-data"),
    DISABLED("Disabled", "manipulator-status--disabled");

    private final String label;
    private final String cssClass;

    ManipulatorReadinessStatus(String label, String cssClass) {
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
