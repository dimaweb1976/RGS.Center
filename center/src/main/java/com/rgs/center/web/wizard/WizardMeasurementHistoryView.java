package com.rgs.center.web.wizard;

import java.util.List;

/** Render-ready models for the stage-11 measurement database pages. */
public final class WizardMeasurementHistoryView {

    private WizardMeasurementHistoryView() {
    }

    public record Search(
            String sampleCode,
            String mode,
            String status,
            String from,
            String to,
            int limit
    ) {
    }

    public record Page(Search search, List<Row> rows) {
        public Page {
            rows = List.copyOf(rows);
        }
    }

    public record Row(
            String mode,
            String runId,
            int objectNumber,
            String sampleCode,
            String startedAt,
            String duration,
            String netMass,
            String status,
            String statusClass,
            String detailUrl
    ) {
    }

    public record Detail(
            String mode,
            String runId,
            int objectNumber,
            String sampleCode,
            String coordinates,
            String grossMass,
            String netMass,
            String measuringTime,
            String volume,
            String geometry,
            String tareMass,
            String startedAt,
            String finishedAt,
            String duration,
            String status,
            String statusClass,
            String spectrumReference,
            String error
    ) {
    }
}
