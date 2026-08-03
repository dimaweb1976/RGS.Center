package com.rgs.center.manipulator;

import com.rgs.center.connection.ManagedDeviceConnection;
import com.rgs.center.connection.TcpDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.FixedLengthFrameDecoder;
import com.rgs.center.peripheral.IncomingFrameRoute;
import com.rgs.center.peripheral.IncomingFrameRouter;
import com.rgs.center.peripheral.PeripheralCommunicationException;
import com.rgs.center.peripheral.PeripheralDeviceWorker;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerState;
import com.rgs.center.peripheral.RoutedFrame;
import com.rgs.center.peripheral.TerminatedFrameDecoder;
import com.rgs.center.service.CurrentDataService;
import com.rgs.center.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Maintains the Dobot command and real-time feedback TCP channels.
 *
 * <p>The command channel uses port 29999 and semicolon-terminated ASCII
 * responses. The independent feedback channel uses fixed 1440-byte packets.
 * Incoming frames are first classified by the common router and only then
 * consumed by request/response or telemetry processing.</p>
 */
@Service
public class RoboticManipulatorStreamService
        implements ManagedDeviceConnection {

    private static final Logger log = LoggerFactory.getLogger(
            RoboticManipulatorStreamService.class
    );
    private static final String COMMAND_SOURCE = "dobot-dashboard";
    private static final String FEEDBACK_SOURCE = "dobot-feedback";
    private static final long IDLE_ROBOT_MODE = 5L;

    private final SettingsService settings;
    private final CurrentDataService currentData;
    private final PeripheralDeviceWorker commandWorker;
    private final PeripheralDeviceWorker feedbackWorker;
    private final IncomingFrameRouter router;
    private final DobotDashboardProtocol dashboardProtocol =
            new DobotDashboardProtocol();
    private final DobotFeedbackParser feedbackParser =
            new DobotFeedbackParser();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<DobotFeedbackFrame> latestFeedback =
            new AtomicReference<>();
    private final AtomicLong latestFeedbackAt = new AtomicLong();
    private final AtomicReference<String> lastError =
            new AtomicReference<>("");
    private final ReentrantLock commandLock = new ReentrantLock();
    private final ReentrantLock feedbackLock = new ReentrantLock();
    private final Condition feedbackChanged = feedbackLock.newCondition();

    private ExecutorService routerExecutor;

    /** Builds both physical channels from centrally managed settings. */
    public RoboticManipulatorStreamService(
            SettingsService settings,
            CurrentDataService currentData
    ) {
        this.settings = settings;
        this.currentData = currentData;
        commandWorker = new PeripheralDeviceWorker(
                COMMAND_SOURCE,
                tcpConnection(COMMAND_SOURCE, SettingsService.MANIPULATOR_COMMAND_PORT),
                new PeripheralWorkerConfiguration(8192, 64, 64, 256, 1024, false),
                new TerminatedFrameDecoder((byte) ';', 1024)
        );
        feedbackWorker = new PeripheralDeviceWorker(
                FEEDBACK_SOURCE,
                tcpConnection(FEEDBACK_SOURCE, SettingsService.MANIPULATOR_FEEDBACK_PORT),
                new PeripheralWorkerConfiguration(16384, 8, 64, 4096, 1440, false),
                new FixedLengthFrameDecoder(DobotFeedbackParser.FRAME_LENGTH)
        );
        router = new IncomingFrameRouter(
                (sourceId, frame) -> switch (sourceId) {
                    case COMMAND_SOURCE -> IncomingFrameRoute.COMMAND_RESPONSE;
                    case FEEDBACK_SOURCE -> IncomingFrameRoute.TELEMETRY;
                    default -> IncomingFrameRoute.UNKNOWN;
                },
                128
        );
    }

    /** Creates a TCP transport whose address and timeouts remain configurable. */
    private TcpDeviceConnection tcpConnection(String id, String portSetting) {
        return new TcpDeviceConnection(
                id,
                () -> settings.getValue(SettingsService.MANIPULATOR_HOST, String.class),
                () -> settingInt(portSetting),
                () -> settingInt(SettingsService.MANIPULATOR_CONNECT_TIMEOUT_MILLIS),
                () -> settingInt(SettingsService.MANIPULATOR_READ_TIMEOUT_MILLIS)
        );
    }

    /** {@inheritDoc} */
    @Override
    public DeviceCode deviceCode() {
        return DeviceCode.ROBOTIC_MANIPULATOR;
    }

    /** Opens both sockets and starts routing and telemetry processing threads. */
    @Override
    public synchronized void startCommunication() {
        if (started.get()) {
            return;
        }
        // A failed TCP connection is still a completed availability check.
        // Record its start so /devices can show Last check while the robot is OFFLINE.
        currentData.setValue(
                deviceCode(),
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );
        stopWorkerIfNecessary(commandWorker);
        stopWorkerIfNecessary(feedbackWorker);
        router.clear();
        try {
            commandWorker.start();
            feedbackWorker.start();
            started.set(true);
            routerExecutor = Executors.newFixedThreadPool(
                    3,
                    new ManipulatorThreadFactory()
            );
            routerExecutor.submit(() -> pump(COMMAND_SOURCE, commandWorker));
            routerExecutor.submit(() -> pump(FEEDBACK_SOURCE, feedbackWorker));
            routerExecutor.submit(this::consumeTelemetry);
            lastError.set("");
            log.info("Dobot command and feedback channels started");
        } catch (RuntimeException exception) {
            started.set(false);
            lastError.set(message(exception));
            safeStop(feedbackWorker);
            safeStop(commandWorker);
            markUnavailable(message(exception));
            throw exception;
        }
    }

    /** Stops routing first and then closes both TCP workers. */
    @Override
    public synchronized void stopCommunication() {
        started.set(false);
        if (routerExecutor != null) {
            routerExecutor.shutdownNow();
            routerExecutor = null;
        }
        safeStop(feedbackWorker);
        safeStop(commandWorker);
        signalFeedbackWaiters();
    }

    /** Returns feedback-channel metrics for the generic device contract. */
    @Override
    public PeripheralWorkerSnapshot communicationSnapshot() {
        return feedbackWorker.snapshot();
    }

    /** Returns diagnostics for both physical channels and the frame router. */
    public RoboticManipulatorSnapshot snapshot() {
        return new RoboticManipulatorSnapshot(
                started.get(),
                commandWorker.snapshot(),
                feedbackWorker.snapshot(),
                router.snapshot(),
                latestFeedback.get(),
                latestFeedbackAt.get(),
                lastError.get()
        );
    }

    /**
     * Sends one official Dashboard command and waits for its routed response.
     * Calls are serialized so responses cannot be assigned to another caller.
     */
    public DobotCommandResponse executeCommand(
            String command,
            Duration timeout
    ) {
        requirePositive(timeout);
        commandLock.lock();
        try {
            requireStarted();
            router.clear(IncomingFrameRoute.COMMAND_RESPONSE);
            currentData.setValue(
                    deviceCode(),
                    CurrentDataService.INTERACTION_LASTDATE_IN,
                    Instant.now().getEpochSecond()
            );
            commandWorker.submit(dashboardProtocol.encode(command));
            RoutedFrame frame = router.await(
                    IncomingFrameRoute.COMMAND_RESPONSE,
                    timeout
            ).orElseThrow(() -> new PeripheralCommunicationException(
                    "Dobot command response timeout for " + command
            ));
            DobotCommandResponse response = dashboardProtocol.parse(frame.payload());
            if (!response.successful()) {
                throw new PeripheralCommunicationException(
                        "Dobot rejected command with ErrorID=" + response.errorId()
                );
            }
            return response;
        } catch (RuntimeException exception) {
            recordError(exception);
            throw exception;
        } finally {
            commandLock.unlock();
        }
    }

    /**
     * Sends a motion command and waits until feedback reports its ResultID and
     * robot mode 5 (enabled and idle), as required by the Dobot manual.
     */
    public DobotCommandResponse executeCommandAndAwaitCompletion(
            String command,
            Duration timeout
    ) {
        commandLock.lock();
        try {
            long deadline = System.nanoTime()
                    + requirePositive(timeout).toNanos();
            // Keep the reentrant command lock until feedback confirms the move.
            DobotCommandResponse response = executeCommand(command, timeout);
            OptionalLong resultId = response.firstLongValue();
            if (resultId.isEmpty()) {
                throw new PeripheralCommunicationException(
                        "Dobot motion response contains no ResultID"
                );
            }
            awaitCompletion(resultId.getAsLong(), deadline);
            return response;
        } finally {
            commandLock.unlock();
        }
    }

    /** Waits on a condition signalled by telemetry, without active polling. */
    private void awaitCompletion(long resultId, long deadlineNanos) {
        feedbackLock.lock();
        try {
            while (!isCompleted(latestFeedback.get(), resultId)) {
                requireStarted();
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    throw new PeripheralCommunicationException(
                            "Dobot motion completion timeout for ResultID=" + resultId
                    );
                }
                try {
                    feedbackChanged.awaitNanos(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new PeripheralCommunicationException(
                            "Interrupted while waiting for Dobot motion", exception
                    );
                }
            }
        } finally {
            feedbackLock.unlock();
        }
    }

    /** Moves complete when the current command matches and the robot is idle. */
    private static boolean isCompleted(DobotFeedbackFrame frame, long resultId) {
        return frame != null
                && frame.currentCommandId() == resultId
                && frame.robotMode() == IDLE_ROBOT_MODE;
    }

    /** Moves complete transport frames from a worker into the shared router. */
    private void pump(String source, PeripheralDeviceWorker worker) {
        try {
            while (started.get()) {
                Optional<byte[]> frame = worker.awaitFrame(Duration.ofMillis(250));
                frame.ifPresent(bytes -> router.route(source, bytes));
            }
        } catch (RuntimeException exception) {
            if (started.get()) {
                started.set(false);
                recordError(exception);
                signalFeedbackWaiters();
            }
        }
    }

    /** Parses routed binary packets and publishes current manipulator values. */
    private void consumeTelemetry() {
        while (started.get()) {
            Optional<RoutedFrame> routed = router.await(
                    IncomingFrameRoute.TELEMETRY,
                    Duration.ofMillis(250)
            );
            if (routed.isEmpty()) {
                continue;
            }
            try {
                DobotFeedbackFrame feedback = feedbackParser.parse(
                        routed.get().payload()
                );
                latestFeedback.set(feedback);
                latestFeedbackAt.set(routed.get().receivedAtEpochMillis());
                publish(feedback);
                signalFeedbackWaiters();
            } catch (RuntimeException exception) {
                recordError(exception);
            }
        }
    }

    /** Copies the useful feedback fields into the RoboticManipulator showcase. */
    private void publish(DobotFeedbackFrame value) {
        DeviceCode device = deviceCode();
        CartesianPose pose = value.pose();
        currentData.setValue(device, "x", pose.x());
        currentData.setValue(device, "y", pose.y());
        currentData.setValue(device, "z", pose.z());
        currentData.setValue(device, "rx", pose.rx());
        currentData.setValue(device, "ry", pose.ry());
        currentData.setValue(device, "rz", pose.rz());
        for (int index = 0; index < value.actualJointPositions().size(); index++) {
            currentData.setValue(
                    device,
                    "joint_" + (index + 1),
                    value.actualJointPositions().get(index)
            );
        }
        currentData.setValue(device, "digital_inputs", value.digitalInputs());
        currentData.setValue(device, "digital_outputs", value.digitalOutputs());
        currentData.setValue(device, "robot_mode", value.robotMode());
        currentData.setValue(device, "queue_running", value.queuedCommandRunning());
        currentData.setValue(device, "enabled", value.enabled());
        currentData.setValue(device, "running", value.running());
        currentData.setValue(device, "error_status", value.error());
        currentData.setValue(device, "current_command_id", value.currentCommandId());
        currentData.setValue(device, "safety_state", value.safetyState());
        currentData.setValue(device, "telemetry_stale", false);
        int closedBit = settingInt(SettingsService.MANIPULATOR_HATCH_CLOSED_DI_BIT);
        int openBit = settingInt(SettingsService.MANIPULATOR_HATCH_OPEN_DI_BIT);
        currentData.setValue(device, "di_" + closedBit, bit(value.digitalInputs(), closedBit));
        currentData.setValue(device, "di_" + openBit, bit(value.digitalInputs(), openBit));
        currentData.setValue(device, "spectrometer_hatch_closed", bit(value.digitalInputs(), closedBit));
        currentData.setValue(device, "spectrometer_hatch_open", bit(value.digitalInputs(), openBit));
        currentData.setValue(device, CurrentDataService.PINGED, true);
        currentData.recordDataSuccess(device, value);
        lastError.set("");
    }

    /** Restarts failed channels and marks telemetry unavailable when it is stale. */
    @Scheduled(
            fixedDelayString = "${rgs.device.manipulator.watchdog-interval-millis:1000}",
            initialDelayString = "${rgs.device.manipulator.watchdog-initial-delay-millis:1000}"
    )
    public void maintainConnection() {
        if (!started.get()) {
            try {
                stopCommunication();
                startCommunication();
            } catch (RuntimeException exception) {
                log.debug("Dobot reconnect attempt failed: {}", message(exception));
            }
            return;
        }
        long last = latestFeedbackAt.get();
        long staleAfter = settingLong(SettingsService.MANIPULATOR_TELEMETRY_STALE_MILLIS);
        if (last == 0 || System.currentTimeMillis() - last > staleAfter) {
            currentData.setValue(deviceCode(), "telemetry_stale", true);
            currentData.setValue(deviceCode(), CurrentDataService.PINGED, false);
        }
        if (commandWorker.snapshot().state() == PeripheralWorkerState.FAILED
                || feedbackWorker.snapshot().state() == PeripheralWorkerState.FAILED) {
            started.set(false);
        }
    }

    private void markUnavailable(String error) {
        currentData.setValue(deviceCode(), CurrentDataService.PINGED, false);
        currentData.recordDataFailure(deviceCode(), error);
    }

    private void recordError(RuntimeException exception) {
        String error = message(exception);
        lastError.set(error);
        markUnavailable(error);
        log.warn("Dobot communication error: {}", error);
    }

    private void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException("Dobot communication is not running");
        }
    }

    private static Duration requirePositive(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        return timeout;
    }

    private int settingInt(String code) {
        return Math.toIntExact(settingLong(code));
    }

    private long settingLong(String code) {
        return settings.getValue(code, Long.class);
    }

    private static boolean bit(long mask, int bit) {
        if (bit < 0 || bit > 63) {
            throw new IllegalArgumentException("Digital input bit must be 0..63");
        }
        return (mask & (1L << bit)) != 0;
    }

    private static String message(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void signalFeedbackWaiters() {
        feedbackLock.lock();
        try {
            feedbackChanged.signalAll();
        } finally {
            feedbackLock.unlock();
        }
    }

    private static void stopWorkerIfNecessary(PeripheralDeviceWorker worker) {
        if (worker.snapshot().state() != PeripheralWorkerState.STOPPED) {
            worker.stop();
        }
    }

    private static void safeStop(PeripheralDeviceWorker worker) {
        try {
            stopWorkerIfNecessary(worker);
        } catch (RuntimeException exception) {
            log.debug("Cannot stop Dobot worker cleanly", exception);
        }
    }

    /** Names the three service threads for support diagnostics. */
    private static final class ManipulatorThreadFactory
            implements java.util.concurrent.ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(
                    task,
                    "robotic-manipulator-router-" + counter.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        }
    }
}
