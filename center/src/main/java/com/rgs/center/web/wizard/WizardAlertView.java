package com.rgs.center.web.wizard;

import java.util.Objects;

/** Причина, требующая внимания оператора на главном экране. */
public record WizardAlertView(
        String deviceName,
        String message,
        boolean critical
) {
    public WizardAlertView {
        Objects.requireNonNull(deviceName, "deviceName");
        Objects.requireNonNull(message, "message");
    }

    public String getCssClass() {
        return critical ? "wizard-alert--critical" : "wizard-alert--warning";
    }
}
