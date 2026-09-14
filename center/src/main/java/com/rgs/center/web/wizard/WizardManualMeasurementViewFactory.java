package com.rgs.center.web.wizard;

import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.measurement.manual.ManualMeasurementSnapshot;
import com.rgs.center.measurement.manual.ManualMeasurementState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats manual workflow state without reading any device port. */
@Component
public class WizardManualMeasurementViewFactory {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Clock clock;

    @Autowired
    public WizardManualMeasurementViewFactory() {
        this(Clock.systemUTC());
    }

    WizardManualMeasurementViewFactory(Clock clock) {
        this.clock = clock;
    }

    public WizardManualMeasurementView create(
            ManualMeasurementSnapshot measurement,
            DashboardSnapshot dashboard
    ) {
        ManualMeasurementState state = measurement.state();
        boolean active = state == ManualMeasurementState.PREPARING
                || state == ManualMeasurementState.ACQUIRING
                || state == ManualMeasurementState.CANCELLING;
        boolean terminal = state == ManualMeasurementState.COMPLETED
                || state == ManualMeasurementState.FAILED
                || state == ManualMeasurementState.CANCELLED;
        var equipment = dashboard.equipment();
        Boolean hvRequested = equipment.boson().highVoltageRequested();
        Boolean hvAccepted = equipment.boson().commandAccepted();
        String highVoltage = Boolean.TRUE.equals(hvRequested)
                && Boolean.TRUE.equals(hvAccepted)
                ? "Enabled · " + equipment.boson().requestedHighVoltage() + " V"
                : Boolean.FALSE.equals(hvRequested) && Boolean.TRUE.equals(hvAccepted)
                        ? "Disabled" : "Not confirmed";
        return new WizardManualMeasurementView(
                state.name(),
                "wizard-run-state--" + state.name().toLowerCase(Locale.ROOT),
                headline(state),
                fallback(measurement.currentOperation()),
                measurement.sampleCode(),
                mass(measurement.grossMassGrams()),
                mass(measurement.netMassGrams()),
                equipment.manipulator().hatch().state().name(),
                highVoltage,
                dateTime(measurement.startedAt()),
                dateTime(measurement.estimatedFinishAt()),
                progress(measurement),
                fallback(measurement.spectrumReference()),
                fallback(measurement.lastError()),
                !active,
                state == ManualMeasurementState.IDLE
                        && !measurement.sampleCode().isBlank()
                        && measurement.grossMassGrams() != null,
                state == ManualMeasurementState.ACQUIRING,
                terminal
        );
    }

    private int progress(ManualMeasurementSnapshot value) {
        if (value.state() == ManualMeasurementState.COMPLETED) return 100;
        if (value.startedAt() == null || value.estimatedFinishAt() == null) return 0;
        long total = Duration.between(value.startedAt(), value.estimatedFinishAt()).toMillis();
        long elapsed = Duration.between(value.startedAt(), clock.instant()).toMillis();
        if (total <= 0) return 0;
        return Math.max(0, Math.min(99, Math.toIntExact(elapsed * 100 / total)));
    }

    private String dateTime(Instant value) {
        return value == null ? "—" : DATE_TIME.withZone(ZoneId.systemDefault()).format(value);
    }

    private static String mass(BigDecimal value) {
        return value == null ? "Not read" : value.stripTrailingZeros().toPlainString() + " g";
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String headline(ManualMeasurementState state) {
        return switch (state) {
            case IDLE -> "Prepare a manual measurement";
            case PREPARING -> "Executing device command";
            case ACQUIRING -> "Acquiring spectrum";
            case CANCELLING -> "Stopping acquisition safely";
            case COMPLETED -> "Manual measurement completed";
            case FAILED -> "Manual measurement failed";
            case CANCELLED -> "Manual measurement was stopped";
        };
    }
}
