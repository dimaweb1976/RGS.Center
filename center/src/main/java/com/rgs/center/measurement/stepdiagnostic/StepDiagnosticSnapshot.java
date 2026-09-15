package com.rgs.center.measurement.stepdiagnostic;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only, in-memory step-test session; never a production measurement. */
public record StepDiagnosticSnapshot(
        UUID id,
        Instant createdAt,
        boolean cameraRecognized,
        List<Integer> objectNumbers,
        List<StepDiagnosticEntry> entries,
        int cursor
) {
    public StepDiagnosticSnapshot {
        objectNumbers = List.copyOf(objectNumbers);
        entries = List.copyOf(entries);
        if (cursor < 0 || cursor > entries.size()) {
            throw new IllegalArgumentException("Invalid diagnostic cursor");
        }
    }

    public boolean complete() {
        return cursor == entries.size();
    }

    public int focusObjectNumber() {
        return entries.get(complete() ? entries.size() - 1 : cursor).objectNumber();
    }

    public List<StepDiagnosticEntry> focusSteps() {
        int number = focusObjectNumber();
        return entries.stream().filter(entry -> entry.objectNumber() == number).toList();
    }

    public long skippedCount() {
        return entries.stream().filter(entry -> entry.status() == StepDiagnosticStatus.SKIPPED).count();
    }

    public long completedCount() {
        return entries.stream().filter(entry -> entry.status() == StepDiagnosticStatus.COMPLETED).count();
    }

    public boolean running() {
        return !complete() && entries.get(cursor).status() == StepDiagnosticStatus.RUNNING;
    }
}
