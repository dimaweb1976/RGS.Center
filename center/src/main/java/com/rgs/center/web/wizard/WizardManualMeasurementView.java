package com.rgs.center.web.wizard;

/** Stable Thymeleaf projection of the current manual measurement. */
public record WizardManualMeasurementView(
        String state,
        String stateCssClass,
        String headline,
        String currentOperation,
        String sampleCode,
        String grossMass,
        String netMass,
        String hatchState,
        String highVoltageState,
        String startedAt,
        String estimatedFinishAt,
        int progressPercent,
        String spectrumReference,
        String lastError,
        boolean controlsEnabled,
        boolean canStart,
        boolean canStop,
        boolean canReset
) {
}
