package com.rgs.center.web.wizard;

import com.rgs.center.camera.DetectedBottle;
import com.rgs.center.measurement.workflow.MeasurementObjectSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowConfiguration;
import com.rgs.center.measurement.workflow.MeasurementWorkflowEvent;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowState;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Converts an orchestrator snapshot into stable Thymeleaf values. */
@Component
public class WizardWorkflowViewFactory {

    private static final int CYCLE_STEP_COUNT = 9;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MeasurementWorkflowConfiguration configuration;
    private final Clock clock;

    @Autowired
    public WizardWorkflowViewFactory(
            MeasurementWorkflowConfiguration configuration
    ) {
        this(configuration, Clock.systemDefaultZone());
    }

    WizardWorkflowViewFactory(
            MeasurementWorkflowConfiguration configuration,
            Clock clock
    ) {
        this.configuration = configuration;
        this.clock = clock;
    }

    public WizardWorkflowView create(
            MeasurementWorkflowSnapshot workflow,
            List<DetectedBottle> detectedBottles
    ) {
        List<WizardWorkflowObjectView> objects = workflow.runId() == null
                ? waitingObjects(detectedBottles)
                : workflow.objects().stream().map(this::objectView).toList();
        int total = objects.size();
        int completed = (int) objects.stream()
                .filter(value -> "COMPLETED".equals(value.status()))
                .count();
        int completedSteps = workflow.objects().stream()
                .mapToInt(WizardWorkflowViewFactory::completedSteps)
                .sum();
        int maximumSteps = workflow.objects().size() * CYCLE_STEP_COUNT;
        int percent = maximumSteps == 0
                ? 0
                : Math.min(100, completedSteps * 100 / maximumSteps);
        MeasurementWorkflowState state = workflow.state();
        return new WizardWorkflowView(
                state.name(),
                "wizard-run-state--" + state.name().toLowerCase(Locale.ROOT),
                headline(state),
                details(workflow),
                completed,
                total,
                percent,
                workflow.activeObjectNumber() == null
                        ? "—" : workflow.activeObjectNumber().toString(),
                workflow.activeStep() == null
                        ? "—" : workflow.activeStep().label(),
                estimatedCompletion(workflow),
                state == MeasurementWorkflowState.RUNNING,
                state == MeasurementWorkflowState.PAUSED
                        || state == MeasurementWorkflowState.PAUSING,
                active(state),
                terminal(state),
                objects,
                eventViews(workflow.events())
        );
    }

    private WizardWorkflowObjectView objectView(MeasurementObjectSnapshot value) {
        return new WizardWorkflowObjectView(
                value.number(), decimal(value.x()), decimal(value.y()),
                value.status(), "wizard-object-status--"
                        + value.status().toLowerCase(Locale.ROOT),
                fallback(value.barcode()), mass(value.grossMassGrams()),
                mass(value.netMassGrams()), fallback(value.spectrumReference()),
                fallback(value.error())
        );
    }

    private static List<WizardWorkflowObjectView> waitingObjects(
            List<DetectedBottle> bottles
    ) {
        return bottles.stream().map(value -> new WizardWorkflowObjectView(
                value.number(), decimal(value.centerX()), decimal(value.centerY()),
                "QUEUED", "wizard-object-status--queued",
                "Waiting", "Waiting", "Waiting", "Waiting", "—"
        )).toList();
    }

    private String estimatedCompletion(MeasurementWorkflowSnapshot workflow) {
        if (!active(workflow.state()) || workflow.parameters() == null) return "—";
        long remainingSeconds = 0L;
        for (MeasurementObjectSnapshot object : workflow.objects()) {
            int completedStep = completedSteps(object);
            for (int index = completedStep + 1; index <= CYCLE_STEP_COUNT; index++) {
                remainingSeconds = Math.addExact(
                        remainingSeconds,
                        index == MeasurementWorkflowStep.ACQUIRE_SPECTRUM.ordinal()
                                ? Math.multiplyExact(
                                        workflow.parameters().measuringTimeMinutes(), 60L
                                )
                                : configuration.getEstimatedDeviceOperationDuration().toSeconds()
                );
            }
        }
        remainingSeconds = Math.max(
                0L,
                remainingSeconds - elapsedActiveOperationSeconds(workflow)
        );
        Instant estimated = clock.instant().plusSeconds(remainingSeconds);
        return DATE_TIME.withZone(ZoneId.systemDefault()).format(estimated)
                + " (about " + human(Duration.ofSeconds(remainingSeconds)) + ")";
    }

    private long elapsedActiveOperationSeconds(MeasurementWorkflowSnapshot workflow) {
        if ((workflow.state() != MeasurementWorkflowState.RUNNING
                && workflow.state() != MeasurementWorkflowState.PAUSING)
                || workflow.activeStep() == null) {
            return 0L;
        }
        return workflow.events().stream()
                .filter(event -> event.step() == workflow.activeStep())
                .filter(event -> java.util.Objects.equals(
                        event.objectNumber(), workflow.activeObjectNumber()
                ))
                .filter(event -> event.message().startsWith("Operation started"))
                .map(MeasurementWorkflowEvent::occurredAt)
                .max(Comparator.naturalOrder())
                .map(started -> Math.max(
                        0L,
                        Duration.between(started, clock.instant()).toSeconds()
                ))
                .orElse(0L);
    }

    private static List<WizardWorkflowEventView> eventViews(
            List<MeasurementWorkflowEvent> events
    ) {
        List<MeasurementWorkflowEvent> latest = new ArrayList<>(events);
        latest.sort(Comparator.comparing(MeasurementWorkflowEvent::occurredAt).reversed());
        return latest.stream().limit(40).map(event -> new WizardWorkflowEventView(
                TIME.withZone(ZoneId.systemDefault()).format(event.occurredAt()),
                event.objectNumber() == null
                        ? (event.step() == null ? "Run" : event.step().label())
                        : "Object " + event.objectNumber()
                                + (event.step() == null ? "" : " · " + event.step().label()),
                event.message()
        )).toList();
    }

    private static int completedSteps(MeasurementObjectSnapshot object) {
        if ("COMPLETED".equals(object.status())) return CYCLE_STEP_COUNT;
        MeasurementWorkflowStep step = object.lastCompletedStep();
        return step == null || step == MeasurementWorkflowStep.PRECHECK
                ? 0 : step.ordinal();
    }

    private static String headline(MeasurementWorkflowState state) {
        return switch (state) {
            case IDLE -> "Ready to start";
            case PRECHECK -> "Checking equipment";
            case RUNNING -> "Automatic measurement is running";
            case PAUSING -> "Waiting for a safe pause point";
            case PAUSED -> "Automatic measurement is paused";
            case CANCELLING -> "Stopping equipment safely";
            case CANCELLED -> "Automatic measurement was stopped";
            case COMPLETED -> "Automatic measurement completed";
            case FAILED -> "Automatic measurement failed";
        };
    }

    private static String details(MeasurementWorkflowSnapshot workflow) {
        if (workflow.state() == MeasurementWorkflowState.FAILED) {
            return fallback(workflow.lastError());
        }
        return switch (workflow.state()) {
            case IDLE -> "Recognize objects and start a production run.";
            case PRECHECK -> "No commands are sent until every required check passes.";
            case PAUSING -> "The current confirmed device operation is allowed to finish.";
            case PAUSED -> "Resume continues with the next operation; completed steps are not repeated.";
            case CANCELLING -> "Gamma-PRO and Dobot are receiving safe stop commands.";
            case CANCELLED -> "The remaining objects were not processed.";
            case COMPLETED -> "All objects in the queue have been processed.";
            case RUNNING -> "Every step waits for a protocol-level completion confirmation.";
            case FAILED -> fallback(workflow.lastError());
        };
    }

    private static boolean active(MeasurementWorkflowState state) {
        return state == MeasurementWorkflowState.PRECHECK
                || state == MeasurementWorkflowState.RUNNING
                || state == MeasurementWorkflowState.PAUSING
                || state == MeasurementWorkflowState.PAUSED
                || state == MeasurementWorkflowState.CANCELLING;
    }

    private static boolean terminal(MeasurementWorkflowState state) {
        return state == MeasurementWorkflowState.COMPLETED
                || state == MeasurementWorkflowState.FAILED
                || state == MeasurementWorkflowState.CANCELLED;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String mass(BigDecimal value) {
        return value == null ? "Waiting" : value.stripTrailingZeros().toPlainString() + " g";
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String human(Duration value) {
        long seconds = value.toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
    }
}
