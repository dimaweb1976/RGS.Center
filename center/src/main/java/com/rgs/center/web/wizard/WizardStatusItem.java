package com.rgs.center.web.wizard;

import java.util.Objects;

/** Один компактный индикатор оборудования в верхней панели Wizard. */
public record WizardStatusItem(
        String code,
        String label,
        String value,
        String detail,
        WizardIndicatorState state
) {
    public WizardStatusItem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(state, "state");
    }
}
