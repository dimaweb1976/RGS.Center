package com.rgs.center.nicole;

/** Техническое состояние NICOLE по значениям последнего корректного PONG. */
public enum NicoleReadinessStatus {
    READY("Ready", "nicole-status--ready"),
    ALERT("Alert", "nicole-status--alert"),
    NO_DATA("No data", "nicole-status--no-data"),
    DISABLED("Disabled", "nicole-status--disabled");

    private final String label;
    private final String cssClass;

    NicoleReadinessStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    /** @return текст для web-интерфейса */
    public String getLabel() {
        return label;
    }

    /** @return фиксированный CSS-класс индикатора */
    public String getCssClass() {
        return cssClass;
    }
}
