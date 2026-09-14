package com.rgs.center.web.wizard;

/** Цветовое и текстовое состояние индикатора в общем header Wizard. */
public enum WizardIndicatorState {
    READY("Ready", "wizard-state--ready"),
    WARNING("Warning", "wizard-state--warning"),
    ALERT("Alert", "wizard-state--alert"),
    NO_DATA("No data", "wizard-state--no-data");

    private final String label;
    private final String cssClass;

    WizardIndicatorState(String label, String cssClass) {
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
