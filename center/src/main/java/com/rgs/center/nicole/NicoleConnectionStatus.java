package com.rgs.center.nicole;

/** Состояние TCP-связи, подтверждённое корректным обменом PING/PONG. */
public enum NicoleConnectionStatus {
    ONLINE("Online", "nicole-connection--online"),
    OFFLINE("Offline", "nicole-connection--offline"),
    DISABLED("Disabled", "nicole-connection--disabled");

    private final String label;
    private final String cssClass;

    NicoleConnectionStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    /** @return подпись состояния связи */
    public String getLabel() {
        return label;
    }

    /** @return фиксированный CSS-класс индикатора */
    public String getCssClass() {
        return cssClass;
    }
}
