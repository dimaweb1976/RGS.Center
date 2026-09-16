package com.rgs.center.web.wizardnew;

import com.rgs.center.measurement.history.MeasurementHistoryEntry;
import com.rgs.center.measurement.history.MeasurementMode;
import com.rgs.center.web.wizard.WizardMeasurementHistoryView;
import com.rgs.center.web.wizard.WizardMeasurementHistoryViewFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Groups measured objects by their persisted run ID without inventing files. */
@Component
public class WizardNewHistoryViewFactory {

    private final WizardMeasurementHistoryViewFactory detailFactory;

    public WizardNewHistoryViewFactory(WizardMeasurementHistoryViewFactory detailFactory) {
        this.detailFactory = detailFactory;
    }

    public WizardNewHistoryView create(
            List<MeasurementHistoryEntry> entries,
            WizardMeasurementHistoryView.Page page,
            UUID selectedRunId
    ) {
        if (entries.size() != page.rows().size()) {
            throw new IllegalArgumentException("History entries and rows must align");
        }
        Map<UUID, List<Integer>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            grouped.computeIfAbsent(entries.get(index).runId(), ignored -> new ArrayList<>())
                    .add(index);
        }
        List<WizardNewHistoryView.Run> runs = new ArrayList<>();
        for (var group : grouped.entrySet()) {
            List<WizardNewHistoryView.ObjectRow> objects = new ArrayList<>();
            LinkedHashSet<String> spectrumReferences = new LinkedHashSet<>();
            for (int index : group.getValue()) {
                MeasurementHistoryEntry entry = entries.get(index);
                WizardMeasurementHistoryView.Row row = page.rows().get(index);
                String detailUrl = entry.mode() == MeasurementMode.MANUAL
                        ? "/wizard-new/database/manual/" + entry.runId()
                        : "/wizard-new/database/automatic/" + entry.runId()
                                + "/" + entry.objectNumber();
                objects.add(new WizardNewHistoryView.ObjectRow(row, detailUrl));
                if (entry.spectrumReference() != null
                        && !entry.spectrumReference().isBlank()) {
                    spectrumReferences.add(entry.spectrumReference());
                }
            }
            MeasurementHistoryEntry first = entries.get(group.getValue().getFirst());
            WizardMeasurementHistoryView.Row firstRow = page.rows()
                    .get(group.getValue().getFirst());
            runs.add(new WizardNewHistoryView.Run(
                    group.getKey().toString(), firstRow.mode(), firstRow.startedAt(),
                    firstRow.status(), objects, List.copyOf(spectrumReferences),
                    detailFactory.detail(first)
            ));
        }
        WizardNewHistoryView.Run selected = runs.stream()
                .filter(value -> selectedRunId != null
                        && value.runId().equals(selectedRunId.toString()))
                .findFirst().orElse(runs.isEmpty() ? null : runs.getFirst());
        return new WizardNewHistoryView(runs, selected, entries.size());
    }
}
