package com.rgs.center.web.wizardnew;

import com.rgs.center.web.wizard.WizardMeasurementHistoryView;

import java.util.List;

/** Real-run grouping for the two-pane database; a run is not a fabricated batch. */
public record WizardNewHistoryView(
        List<Run> runs,
        Run selectedRun,
        int measurementsShown
) {
    public WizardNewHistoryView {
        runs = List.copyOf(runs);
    }

    public record Run(
            String runId,
            String mode,
            String startedAt,
            String status,
            List<ObjectRow> objects,
            List<String> spectrumReferences,
            WizardMeasurementHistoryView.Detail parameters
    ) {
        public Run {
            objects = List.copyOf(objects);
            spectrumReferences = List.copyOf(spectrumReferences);
        }
    }

    public record ObjectRow(
            WizardMeasurementHistoryView.Row row,
            String newDetailUrl
    ) {
    }
}
