package com.rgs.center.manipulator;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerState;
import com.rgs.center.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Вычисляет read-only статусы Dobot исключительно по последнему snapshot. */
@Component
public class ManipulatorStatusEvaluator {

    private static final long IDLE_ROBOT_MODE = 5L;
    private final SettingsService settingsService;
    private final Clock clock;

    @Autowired
    public ManipulatorStatusEvaluator(SettingsService settingsService) {
        this(settingsService, Clock.systemUTC());
    }

    ManipulatorStatusEvaluator(SettingsService settingsService, Clock clock) {
        this.settingsService = settingsService;
        this.clock = clock;
    }

    /**
     * @param enabled разрешена ли связь настройкой устройства
     * @param snapshot последний согласованный снимок двух каналов
     * @return связь, готовность, положение люка и причины состояния
     */
    public ManipulatorStatusView evaluate(
            boolean enabled,
            RoboticManipulatorSnapshot snapshot
    ) {
        if (!enabled) {
            return new ManipulatorStatusView(
                    ManipulatorConnectionStatus.DISABLED,
                    ManipulatorReadinessStatus.DISABLED,
                    SpectrometerHatchStatus.UNKNOWN,
                    List.of("Device is disabled by configuration")
            );
        }
        if (snapshot == null) {
            return noData(ManipulatorConnectionStatus.OFFLINE);
        }

        DobotFeedbackFrame feedback = snapshot.latestFeedback();
        boolean hasFeedback = feedback != null
                && snapshot.latestFeedbackReceivedAtEpochMillis() > 0L;
        boolean channelsRunning = running(snapshot.commandChannel())
                && running(snapshot.feedbackChannel());
        boolean channelsTransitioning = transitioning(snapshot.commandChannel())
                || transitioning(snapshot.feedbackChannel());
        boolean stale = hasFeedback && isStale(
                snapshot.latestFeedbackReceivedAtEpochMillis()
        );

        ManipulatorConnectionStatus connectionStatus;
        if (snapshot.started() && channelsRunning && hasFeedback && !stale) {
            connectionStatus = ManipulatorConnectionStatus.ONLINE;
        } else if (snapshot.started()
                && (channelsTransitioning || channelsRunning && !hasFeedback)) {
            connectionStatus = ManipulatorConnectionStatus.CONNECTING;
        } else {
            connectionStatus = ManipulatorConnectionStatus.OFFLINE;
        }

        if (!hasFeedback) {
            return noData(connectionStatus);
        }

        SpectrometerHatchStatus hatch = hatchStatus(feedback.digitalInputs());
        List<String> reasons = new ArrayList<>();
        if (stale) reasons.add("Telemetry is stale");
        if (!channelsRunning) reasons.add("One or both TCP channels are not running");
        if (feedback.error()) reasons.add("Robot feedback reports an error");
        reasons.addAll(
                DobotSafetyState.decode(feedback.safetyState()).blockingFaults()
        );
        if (hatch == SpectrometerHatchStatus.INVALID) {
            reasons.add("Both hatch limit switches are active");
        }
        if (snapshot.lastError() != null && !snapshot.lastError().isBlank()) {
            reasons.add(snapshot.lastError());
        }

        ManipulatorReadinessStatus readiness;
        if (!reasons.isEmpty()
                || connectionStatus != ManipulatorConnectionStatus.ONLINE) {
            readiness = ManipulatorReadinessStatus.ALERT;
        } else if (feedback.running() || feedback.queuedCommandRunning()) {
            readiness = ManipulatorReadinessStatus.BUSY;
        } else if (feedback.enabled()
                && feedback.robotMode() == IDLE_ROBOT_MODE) {
            readiness = ManipulatorReadinessStatus.READY;
        } else {
            readiness = ManipulatorReadinessStatus.ALERT;
            reasons.add("Robot is not enabled and idle");
        }
        return new ManipulatorStatusView(
                connectionStatus,
                readiness,
                hatch,
                reasons
        );
    }

    private ManipulatorStatusView noData(
            ManipulatorConnectionStatus connectionStatus
    ) {
        return new ManipulatorStatusView(
                connectionStatus,
                ManipulatorReadinessStatus.NO_DATA,
                SpectrometerHatchStatus.UNKNOWN,
                List.of("Feedback has not been received")
        );
    }

    private boolean isStale(long receivedAtMillis) {
        long staleAfter = settingsService.getValue(
                SettingsService.MANIPULATOR_TELEMETRY_STALE_MILLIS,
                Long.class
        );
        return clock.millis() - receivedAtMillis > staleAfter;
    }

    private SpectrometerHatchStatus hatchStatus(long inputs) {
        boolean closed = bit(inputs, settingBit(
                SettingsService.MANIPULATOR_HATCH_CLOSED_DI_BIT
        ));
        boolean open = bit(inputs, settingBit(
                SettingsService.MANIPULATOR_HATCH_OPEN_DI_BIT
        ));
        if (closed && open) return SpectrometerHatchStatus.INVALID;
        if (closed) return SpectrometerHatchStatus.CLOSED;
        if (open) return SpectrometerHatchStatus.OPEN;
        return SpectrometerHatchStatus.INTERMEDIATE;
    }

    private int settingBit(String code) {
        long value = settingsService.getValue(code, Long.class);
        if (value < 0 || value > 63) {
            throw new IllegalStateException("Invalid digital input bit: " + value);
        }
        return Math.toIntExact(value);
    }

    private static boolean bit(long mask, int bit) {
        return ((mask >>> bit) & 1L) == 1L;
    }

    private static boolean running(PeripheralWorkerSnapshot snapshot) {
        return snapshot != null && snapshot.state() == PeripheralWorkerState.RUNNING;
    }

    private static boolean transitioning(PeripheralWorkerSnapshot snapshot) {
        return snapshot != null && (snapshot.state() == PeripheralWorkerState.STARTING
                || snapshot.state() == PeripheralWorkerState.STOPPING);
    }
}
