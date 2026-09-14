package com.rgs.center.web.wizard;

import com.rgs.center.measurement.history.MeasurementHistoryCriteria;
import com.rgs.center.measurement.history.MeasurementHistoryEntry;
import com.rgs.center.measurement.history.MeasurementMode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Formats database values without putting presentation logic in SQL or HTML. */
@Component
public class WizardMeasurementHistoryViewFactory {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public WizardMeasurementHistoryView.Page page(
            MeasurementHistoryCriteria criteria,
            String from,
            String to,
            List<MeasurementHistoryEntry> entries
    ) {
        return new WizardMeasurementHistoryView.Page(
                new WizardMeasurementHistoryView.Search(
                        criteria.sampleCode(),
                        criteria.mode() == null ? "" : criteria.mode().name(),
                        criteria.status(),
                        from,
                        to,
                        criteria.limit()
                ),
                entries.stream().map(this::row).toList()
        );
    }

    public WizardMeasurementHistoryView.Detail detail(MeasurementHistoryEntry value) {
        return new WizardMeasurementHistoryView.Detail(
                label(value.mode()),
                value.runId().toString(),
                value.objectNumber(),
                fallback(value.sampleCode()),
                coordinates(value),
                mass(value.grossMassGrams()),
                mass(value.netMassGrams()),
                value.measuringTimeMinutes() + " min",
                decimal(value.volumeMl(), 1) + " ml",
                value.geometry().getLabel(),
                decimal(value.tareMassGrams(), 3) + " g",
                date(value.startedAt()),
                date(value.finishedAt()),
                duration(value.startedAt(), value.finishedAt()),
                value.status(),
                statusClass(value.status()),
                fallback(value.spectrumReference()),
                fallback(value.error())
        );
    }

    private WizardMeasurementHistoryView.Row row(MeasurementHistoryEntry value) {
        String detailUrl = value.mode() == MeasurementMode.MANUAL
                ? "/wizard/database/manual/" + value.runId()
                : "/wizard/database/automatic/" + value.runId()
                        + "/" + value.objectNumber();
        return new WizardMeasurementHistoryView.Row(
                label(value.mode()),
                value.runId().toString(),
                value.objectNumber(),
                fallback(value.sampleCode()),
                date(value.startedAt()),
                duration(value.startedAt(), value.finishedAt()),
                mass(value.netMassGrams()),
                value.status(),
                statusClass(value.status()),
                detailUrl
        );
    }

    private static String label(MeasurementMode mode) {
        return mode == MeasurementMode.MANUAL ? "Manual" : "Automatic";
    }

    private static String statusClass(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> "ready";
            case "CANCELLED" -> "warning";
            default -> "alert";
        };
    }

    private static String coordinates(MeasurementHistoryEntry value) {
        if (value.x() == null || value.y() == null) {
            return "Not applicable (manual mode)";
        }
        return "X " + decimal(value.x(), 2) + ", Y " + decimal(value.y(), 2);
    }

    private static String duration(Instant started, Instant finished) {
        Duration value = Duration.between(started, finished);
        long seconds = Math.max(0, value.toSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? "%d h %02d min %02d s".formatted(hours, minutes, remainder)
                : "%d min %02d s".formatted(minutes, remainder);
    }

    private static String date(Instant value) {
        return DATE_TIME.format(value);
    }

    private static String mass(BigDecimal value) {
        return value == null ? "Not recorded" : value.setScale(3, RoundingMode.HALF_UP) + " g";
    }

    private static String decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "Not recorded" : value;
    }
}
