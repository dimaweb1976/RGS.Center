package com.rgs.center.measurement.workflow;

import com.rgs.center.measurement.ScenarioStatus;
import com.rgs.center.measurement.ScenarioStatusService;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import com.rgs.center.measurement.history.MeasurementHistoryRepository;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Production state machine for the sequential processing of detected objects.
 * HTTP controllers only start/control it; all device calls run outside request
 * threads and pass through {@link MeasurementWorkflowDeviceGateway}.
 */
@Service
public class ProductionMeasurementOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(
            ProductionMeasurementOrchestrator.class
    );
    private static final int MAX_EVENTS = 200;

    private final MeasurementWorkflowDeviceGateway gateway;
    private final MeasurementParametersService parametersService;
    private final ScenarioStatusService scenarioStatusService;
    private final MeasurementExecutionCoordinator executionCoordinator;
    private final MeasurementWorkflowConfiguration configuration;
    private final MeasurementHistoryRepository historyRepository;
    private final Clock clock;
    private final ExecutorService runExecutor;
    private final ExecutorService operationExecutor;

    private final List<MutableObject> objects = new ArrayList<>();
    private final ArrayDeque<MeasurementWorkflowEvent> events = new ArrayDeque<>();
    private UUID runId;
    private MeasurementWorkflowState state = MeasurementWorkflowState.IDLE;
    private MeasurementWorkflowStep activeStep;
    private Integer activeObjectNumber;
    private Instant startedAt;
    private Instant finishedAt;
    private MeasurementParameters parameters;
    private String lastError = "";
    private boolean cancellationRequested;
    private boolean pauseRequested;
    private Future<?> runTask;
    private Future<?> activeOperation;

    @Autowired
    public ProductionMeasurementOrchestrator(
            MeasurementWorkflowDeviceGateway gateway,
            MeasurementParametersService parametersService,
            ScenarioStatusService scenarioStatusService,
            MeasurementExecutionCoordinator executionCoordinator,
            MeasurementWorkflowConfiguration configuration,
            MeasurementHistoryRepository historyRepository
    ) {
        this(
                gateway,
                parametersService,
                scenarioStatusService,
                executionCoordinator,
                configuration,
                historyRepository,
                Clock.systemUTC(),
                Executors.newSingleThreadExecutor(runnable -> daemon(
                        runnable,
                        "measurement-workflow"
                )),
                Executors.newCachedThreadPool(runnable -> daemon(
                        runnable,
                        "measurement-device-operation"
                ))
        );
    }

    /** Package-level constructor keeps isolated state-machine tests database-free. */
    ProductionMeasurementOrchestrator(
            MeasurementWorkflowDeviceGateway gateway,
            MeasurementParametersService parametersService,
            ScenarioStatusService scenarioStatusService,
            MeasurementExecutionCoordinator executionCoordinator,
            MeasurementWorkflowConfiguration configuration
    ) {
        this(
                gateway, parametersService, scenarioStatusService,
                executionCoordinator, configuration, null,
                Clock.systemUTC(),
                Executors.newSingleThreadExecutor(runnable -> daemon(
                        runnable, "measurement-workflow"
                )),
                Executors.newCachedThreadPool(runnable -> daemon(
                        runnable, "measurement-device-operation"
                ))
        );
    }

    ProductionMeasurementOrchestrator(
            MeasurementWorkflowDeviceGateway gateway,
            MeasurementParametersService parametersService,
            ScenarioStatusService scenarioStatusService,
            MeasurementExecutionCoordinator executionCoordinator,
            MeasurementWorkflowConfiguration configuration,
            MeasurementHistoryRepository historyRepository,
            Clock clock,
            ExecutorService runExecutor,
            ExecutorService operationExecutor
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.parametersService = Objects.requireNonNull(parametersService, "parametersService");
        this.scenarioStatusService = Objects.requireNonNull(scenarioStatusService, "scenarioStatusService");
        this.executionCoordinator = Objects.requireNonNull(
                executionCoordinator, "executionCoordinator"
        );
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.historyRepository = historyRepository;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.runExecutor = Objects.requireNonNull(runExecutor, "runExecutor");
        this.operationExecutor = Objects.requireNonNull(operationExecutor, "operationExecutor");
    }

    /**
     * Queues one production run for the automatic Thymeleaf workflow.
     *
     * @return unique identifier of the accepted run
     */
    public synchronized UUID start(List<MeasurementObjectRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        if (isActive()) {
            throw new IllegalStateException("A measurement workflow is already active");
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("At least one object is required");
        }
        configuration.validate();
        List<MeasurementObjectRequest> queue = List.copyOf(requests);
        ensureUniqueNumbers(queue);
        executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.AUTOMATIC);

        try {
            runId = UUID.randomUUID();
            state = MeasurementWorkflowState.PRECHECK;
            activeStep = MeasurementWorkflowStep.PRECHECK;
            activeObjectNumber = null;
            startedAt = clock.instant();
            finishedAt = null;
            parameters = parametersService.current();
            lastError = "";
            cancellationRequested = false;
            pauseRequested = false;
            objects.clear();
            queue.stream().map(MutableObject::new).forEach(objects::add);
            events.clear();
            event(null, MeasurementWorkflowStep.PRECHECK, 0,
                    "Run accepted; equipment preflight started");
            scenarioStatusService.updateStatus(
                    ScenarioStatus.NORMAL,
                    "production measurement workflow started"
            );
            runTask = runExecutor.submit(() -> execute(queue, parameters));
        } catch (RuntimeException exception) {
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.AUTOMATIC);
            throw exception;
        }
        log.info("Production measurement workflow accepted: runId={}, objects={}",
                runId, queue.size());
        return runId;
    }

    /** Requests a pause at the next confirmed boundary between operations. */
    public boolean pause() {
        synchronized (this) {
            if (state != MeasurementWorkflowState.RUNNING || pauseRequested) {
                return false;
            }
            pauseRequested = true;
            state = activeOperation == null
                    ? MeasurementWorkflowState.PAUSED
                    : MeasurementWorkflowState.PAUSING;
            event(activeObjectNumber, activeStep, 0,
                    "Pause requested; waiting for a safe operation boundary");
        }
        scenarioStatusService.updateStatus(
                ScenarioStatus.PAUSED,
                "production workflow pause requested"
        );
        return true;
    }

    /** Continues a paused run without repeating the last confirmed operation. */
    public boolean resume() {
        synchronized (this) {
            if ((state != MeasurementWorkflowState.PAUSED
                    && state != MeasurementWorkflowState.PAUSING)
                    || !pauseRequested) {
                return false;
            }
            pauseRequested = false;
            state = MeasurementWorkflowState.RUNNING;
            event(activeObjectNumber, activeStep, 0, "Run resumed");
            notifyAll();
        }
        scenarioStatusService.updateStatus(
                ScenarioStatus.NORMAL,
                "production workflow resumed"
        );
        return true;
    }

    /** Requests cancellation and interrupts the currently waiting operation. */
    public boolean cancel() {
        Future<?> operation;
        synchronized (this) {
            if (!isActive() || cancellationRequested) {
                return false;
            }
            cancellationRequested = true;
            pauseRequested = false;
            state = MeasurementWorkflowState.CANCELLING;
            event(activeObjectNumber, activeStep, 0, "Cancellation requested");
            operation = activeOperation;
            notifyAll();
        }
        scenarioStatusService.updateStatus(
                ScenarioStatus.ABORTING,
                "production workflow cancellation requested"
        );
        if (operation != null) {
            operation.cancel(true);
        }
        gateway.cancelActiveOperation();
        return true;
    }

    /** @return a consistent snapshot without touching any device port */
    public synchronized MeasurementWorkflowSnapshot snapshot() {
        return new MeasurementWorkflowSnapshot(
                runId,
                state,
                activeStep,
                activeObjectNumber,
                startedAt,
                finishedAt,
                parameters,
                lastError,
                objects.stream().map(MutableObject::snapshot).toList(),
                List.copyOf(events)
        );
    }

    private void execute(
            List<MeasurementObjectRequest> queue,
            MeasurementParameters runParameters
    ) {
        try {
            WorkflowPreflightReport report = runWithPolicy(
                    null,
                    MeasurementWorkflowStep.PRECHECK,
                    () -> gateway.preflight(queue, runParameters),
                    configuration.getOperationTimeout(),
                    1
            );
            if (!report.isReady()) {
                throw new IllegalStateException(
                        "Preflight failed: " + String.join("; ", report.issues())
                );
            }
            synchronized (this) {
                state = MeasurementWorkflowState.RUNNING;
                event(null, MeasurementWorkflowStep.PRECHECK, 1,
                        "Equipment preflight completed");
            }

            for (MeasurementObjectRequest request : queue) {
                checkCancellation();
                processObject(request, runParameters);
            }
            synchronized (this) {
                state = MeasurementWorkflowState.COMPLETED;
                activeStep = null;
                activeObjectNumber = null;
                finishedAt = clock.instant();
                event(null, null, 0, "All queued objects completed");
            }
            scenarioStatusService.updateStatus(
                    ScenarioStatus.NORMAL,
                    "production measurement workflow completed"
            );
            persistTerminalRun();
            log.info("Production measurement workflow completed: runId={}", runId);
        } catch (WorkflowCancelledException | CancellationException exception) {
            finishCancelled();
        } catch (RuntimeException exception) {
            if (isCancellationRequested()) {
                finishCancelled();
            } else {
                finishFailed(exception);
            }
        } finally {
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.AUTOMATIC);
        }
    }

    private void processObject(
            MeasurementObjectRequest request,
            MeasurementParameters runParameters
    ) {
        MutableObject object = object(request.number());
        synchronized (this) {
            activeObjectNumber = request.number();
            object.status = "RUNNING";
        }

        runVoid(request, MeasurementWorkflowStep.PICK_OBJECT,
                () -> gateway.pickObject(request));
        runVoid(request, MeasurementWorkflowStep.MOVE_TO_BARCODE,
                () -> gateway.moveToBarcode(request));
        String barcode = runWithPolicy(request, MeasurementWorkflowStep.READ_BARCODE,
                () -> gateway.readBarcode(request), timeoutFor(MeasurementWorkflowStep.READ_BARCODE),
                configuration.getMaxAttempts());
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalStateException("Barcode scanner returned an empty value");
        }
        synchronized (this) { object.barcode = barcode.trim(); }

        runVoid(request, MeasurementWorkflowStep.MOVE_TO_SCALES,
                () -> gateway.moveToScales(request));
        BigDecimal gross = runWithPolicy(request, MeasurementWorkflowStep.READ_WEIGHT,
                () -> gateway.readWeight(request), timeoutFor(MeasurementWorkflowStep.READ_WEIGHT),
                configuration.getMaxAttempts());
        if (gross == null) {
            throw new IllegalStateException("Scales returned no weight");
        }
        synchronized (this) {
            object.grossMass = gross;
            object.netMass = gross.subtract(BigDecimal.valueOf(runParameters.tareMassGrams()));
            if (object.netMass.signum() < 0) {
                throw new IllegalStateException(
                        "Gross weight is below configured tare mass"
                );
            }
        }

        runVoid(request, MeasurementWorkflowStep.MOVE_TO_SPECTROMETER,
                () -> gateway.moveToSpectrometer(request));
        String spectrum = runWithPolicy(request, MeasurementWorkflowStep.ACQUIRE_SPECTRUM,
                () -> gateway.acquireSpectrum(request, runParameters),
                spectrumTimeout(runParameters), 1);
        synchronized (this) { object.spectrumReference = normalize(spectrum); }

        runVoid(request, MeasurementWorkflowStep.REMOVE_FROM_SPECTROMETER,
                () -> gateway.removeFromSpectrometer(request));
        runVoid(request, MeasurementWorkflowStep.RETURN_OBJECT,
                () -> gateway.returnObject(request));
        synchronized (this) {
            object.status = "COMPLETED";
            event(request.number(), null, 0, "Object processing completed");
        }
    }

    private void runVoid(
            MeasurementObjectRequest object,
            MeasurementWorkflowStep step,
            Runnable operation
    ) {
        runWithPolicy(object, step, () -> {
            operation.run();
            return null;
        }, timeoutFor(step), 1);
    }

    private <T> T runWithPolicy(
            MeasurementObjectRequest object,
            MeasurementWorkflowStep step,
            Callable<T> operation,
            Duration timeout,
            int maxAttempts
    ) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            awaitIfPaused();
            checkCancellation();
            synchronized (this) {
                activeStep = step;
                activeObjectNumber = object == null ? null : object.number();
                event(activeObjectNumber, step, attempt,
                        "Operation started (attempt " + attempt + "/" + maxAttempts + ")");
            }
            Future<T> future = operationExecutor.submit(operation);
            synchronized (this) { activeOperation = future; }
            try {
                T result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                synchronized (this) {
                    if (object != null) {
                        object(object.number()).lastCompletedStep = step;
                    }
                    event(activeObjectNumber, step, attempt, "Operation confirmed");
                }
                return result;
            } catch (TimeoutException exception) {
                future.cancel(true);
                gateway.cancelActiveOperation();
                lastFailure = new IllegalStateException(
                        step.label() + " timed out after " + timeout,
                        exception
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new WorkflowCancelledException();
            } catch (CancellationException exception) {
                throw new WorkflowCancelledException();
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                lastFailure = cause instanceof RuntimeException runtime
                        ? runtime
                        : new IllegalStateException(step.label() + " failed", cause);
            } finally {
                synchronized (this) {
                    if (activeOperation == future) activeOperation = null;
                }
            }
            synchronized (this) {
                event(activeObjectNumber, step, attempt,
                        "Operation failed: " + errorMessage(lastFailure));
            }
        }
        throw lastFailure == null
                ? new IllegalStateException(step.label() + " failed")
                : lastFailure;
    }

    private Duration timeoutFor(MeasurementWorkflowStep step) {
        return configuration.getOperationTimeout();
    }

    private Duration spectrumTimeout(MeasurementParameters value) {
        return Duration.ofMinutes(value.measuringTimeMinutes())
                .plus(configuration.getSpectrumOverrun());
    }

    private synchronized MutableObject object(int number) {
        return objects.stream().filter(value -> value.request.number() == number)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown workflow object: " + number));
    }

    private synchronized boolean isActive() {
        return state == MeasurementWorkflowState.PRECHECK
                || state == MeasurementWorkflowState.RUNNING
                || state == MeasurementWorkflowState.PAUSING
                || state == MeasurementWorkflowState.PAUSED
                || state == MeasurementWorkflowState.CANCELLING;
    }

    /** Waits efficiently until Resume/Stop; no active polling is used. */
    private void awaitIfPaused() {
        synchronized (this) {
            boolean announced = false;
            while (pauseRequested && !cancellationRequested) {
                if (!announced) {
                    state = MeasurementWorkflowState.PAUSED;
                    event(activeObjectNumber, activeStep, 0,
                            "Run paused at a safe operation boundary");
                    announced = true;
                }
                try {
                    wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new WorkflowCancelledException();
                }
            }
        }
    }

    private synchronized boolean isCancellationRequested() {
        return cancellationRequested;
    }

    private void checkCancellation() {
        if (isCancellationRequested() || Thread.currentThread().isInterrupted()) {
            throw new WorkflowCancelledException();
        }
    }

    private void finishCancelled() {
        synchronized (this) {
            state = MeasurementWorkflowState.CANCELLED;
            activeStep = null;
            activeObjectNumber = null;
            finishedAt = clock.instant();
            objects.stream().filter(value -> "RUNNING".equals(value.status))
                    .forEach(value -> value.status = "CANCELLED");
            event(null, null, 0, "Run cancelled safely");
        }
        scenarioStatusService.updateStatus(
                ScenarioStatus.NORMAL,
                "production workflow cancelled"
        );
        persistTerminalRun();
        log.info("Production measurement workflow cancelled: runId={}", runId);
    }

    private void finishFailed(RuntimeException exception) {
        String message = errorMessage(exception);
        synchronized (this) {
            state = MeasurementWorkflowState.FAILED;
            lastError = message;
            finishedAt = clock.instant();
            objects.stream().filter(value -> "RUNNING".equals(value.status))
                    .forEach(value -> {
                        value.status = "FAILED";
                        value.error = message;
                    });
            event(activeObjectNumber, activeStep, 0, "Run failed: " + message);
            activeStep = null;
            activeObjectNumber = null;
        }
        scenarioStatusService.updateStatus(ScenarioStatus.EMERGENCY, message);
        persistTerminalRun();
        log.error("Production measurement workflow failed: runId={}, error={}", runId, message, exception);
    }

    /** History failures are reported but never rewrite the physical run outcome. */
    private void persistTerminalRun() {
        if (historyRepository == null) {
            return;
        }
        try {
            historyRepository.insertAutomaticRun(snapshot());
        } catch (RuntimeException exception) {
            log.error(
                    "Automatic measurement history was not saved: runId={}",
                    runId,
                    exception
            );
        }
    }

    private synchronized void event(
            Integer objectNumber,
            MeasurementWorkflowStep step,
            int attempt,
            String message
    ) {
        while (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(new MeasurementWorkflowEvent(
                clock.instant(), objectNumber, step, attempt, message
        ));
    }

    @PreDestroy
    void shutdown() {
        cancel();
        runExecutor.shutdownNow();
        operationExecutor.shutdownNow();
    }

    private static void ensureUniqueNumbers(List<MeasurementObjectRequest> queue) {
        long unique = queue.stream().map(MeasurementObjectRequest::number).distinct().count();
        if (unique != queue.size()) {
            throw new IllegalArgumentException("Object numbers must be unique");
        }
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static String errorMessage(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static final class WorkflowCancelledException extends RuntimeException {
    }

    private static final class MutableObject {
        private final MeasurementObjectRequest request;
        private String status = "QUEUED";
        private MeasurementWorkflowStep lastCompletedStep;
        private String barcode = "";
        private BigDecimal grossMass;
        private BigDecimal netMass;
        private String spectrumReference = "";
        private String error = "";

        private MutableObject(MeasurementObjectRequest request) {
            this.request = request;
        }

        private MeasurementObjectSnapshot snapshot() {
            return new MeasurementObjectSnapshot(
                    request.number(), request.x(), request.y(), status,
                    lastCompletedStep, barcode, grossMass, netMass,
                    spectrumReference, error
            );
        }
    }
}
