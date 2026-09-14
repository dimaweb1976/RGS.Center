package com.rgs.center.web.wizard;

import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.measurement.workflow.MeasurementObjectSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowConfiguration;
import com.rgs.center.measurement.workflow.MeasurementWorkflowEvent;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowState;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WizardWorkflowViewFactoryTests {

    private final WizardWorkflowViewFactory factory = new WizardWorkflowViewFactory(
            new MeasurementWorkflowConfiguration(),
            Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void marksCompletedCurrentAndWaitingCycleOperations() {
        WizardWorkflowView view = factory.create(snapshot(
                MeasurementWorkflowState.RUNNING,
                MeasurementWorkflowStep.READ_BARCODE,
                "RUNNING",
                MeasurementWorkflowStep.MOVE_TO_BARCODE,
                List.of()
        ), List.of());

        assertThat(view.cycleSteps())
                .extracting(WizardWorkflowStepView::status)
                .containsExactly(
                        "COMPLETED", "COMPLETED", "RUNNING",
                        "WAITING", "WAITING", "WAITING",
                        "WAITING", "WAITING", "WAITING"
                );
    }

    @Test
    void restoresFailedOperationFromTerminalWorkflowJournal() {
        Instant failedAt = Instant.parse("2026-09-14T11:59:00Z");
        WizardWorkflowView view = factory.create(snapshot(
                MeasurementWorkflowState.FAILED,
                null,
                "FAILED",
                MeasurementWorkflowStep.MOVE_TO_SCALES,
                List.of(new MeasurementWorkflowEvent(
                        failedAt, 1, MeasurementWorkflowStep.READ_WEIGHT, 0,
                        "Run failed: stable weight was not received"
                ))
        ), List.of());

        assertThat(view.cycleSteps())
                .extracting(WizardWorkflowStepView::status)
                .containsExactly(
                        "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED",
                        "ERROR", "WAITING", "WAITING", "WAITING", "WAITING"
                );
    }

    private static MeasurementWorkflowSnapshot snapshot(
            MeasurementWorkflowState state,
            MeasurementWorkflowStep activeStep,
            String objectStatus,
            MeasurementWorkflowStep lastCompletedStep,
            List<MeasurementWorkflowEvent> events
    ) {
        Instant startedAt = Instant.parse("2026-09-14T11:55:00Z");
        return new MeasurementWorkflowSnapshot(
                UUID.fromString("8f6ddc93-8468-4297-b4c9-8a11ab990eba"),
                state,
                activeStep,
                activeStep == null ? null : 1,
                startedAt,
                state == MeasurementWorkflowState.FAILED
                        ? Instant.parse("2026-09-14T11:59:00Z") : null,
                new MeasurementParameters(
                        1, 300, SampleGeometry.BOTTLE, 25, 1, startedAt
                ),
                state == MeasurementWorkflowState.FAILED ? "Weight failed" : "",
                List.of(new MeasurementObjectSnapshot(
                        1, 125.5, 64.0, objectStatus, lastCompletedStep,
                        "", null, null, "", ""
                )),
                events
        );
    }
}
