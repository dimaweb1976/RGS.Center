package com.rgs.center.measurement.manual;

import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Coordinates one operator-driven measurement without owning device transports. */
@Service
public class ManualMeasurementService {

    private static final Logger log = LoggerFactory.getLogger(
            ManualMeasurementService.class
    );

    private final ManualMeasurementDeviceGateway gateway;
    private final ManualMeasurementRepository repository;
    private final MeasurementParametersService parametersService;
    private final MeasurementExecutionCoordinator executionCoordinator;
    private final Clock clock;
    private final ExecutorService acquisitionExecutor;

    private UUID measurementId;
    private ManualMeasurementState state = ManualMeasurementState.IDLE;
    private String currentOperation = "";
    private String sampleCode = "";
    private BigDecimal grossMass;
    private BigDecimal netMass;
    private MeasurementParameters parameters;
    private Instant startedAt;
    private Instant estimatedFinishAt;
    private Instant finishedAt;
    private String spectrumReference = "";
    private String lastError = "";
    private boolean resultSaved;
    private Future<?> acquisitionTask;

    @Autowired
    public ManualMeasurementService(
            ManualMeasurementDeviceGateway gateway,
            ManualMeasurementRepository repository,
            MeasurementParametersService parametersService,
            MeasurementExecutionCoordinator executionCoordinator
    ) {
        this(
                gateway,
                repository,
                parametersService,
                executionCoordinator,
                Clock.systemUTC(),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "manual-measurement");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    ManualMeasurementService(
            ManualMeasurementDeviceGateway gateway,
            ManualMeasurementRepository repository,
            MeasurementParametersService parametersService,
            MeasurementExecutionCoordinator executionCoordinator,
            Clock clock,
            ExecutorService acquisitionExecutor
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.parametersService = Objects.requireNonNull(parametersService, "parametersService");
        this.executionCoordinator = Objects.requireNonNull(
                executionCoordinator, "executionCoordinator"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.acquisitionExecutor = Objects.requireNonNull(
                acquisitionExecutor, "acquisitionExecutor"
        );
    }

    public void setSampleCode(String value) {
        String normalized = normalizeCode(value);
        synchronized (this) {
            requireDataEntryState();
            sampleCode = normalized;
            lastError = "";
        }
    }

    public String scanBarcode() {
        synchronized (this) { requireDataEntryState(); }
        String value = preparation("Scanning barcode", gateway::scanBarcode);
        synchronized (this) {
            sampleCode = normalizeCode(value);
            return sampleCode;
        }
    }

    public BigDecimal readWeight() {
        synchronized (this) { requireDataEntryState(); }
        BigDecimal value = preparation("Reading stable weight", gateway::readWeight);
        if (value == null) throw new IllegalStateException("Scales returned no weight");
        synchronized (this) {
            grossMass = value;
            MeasurementParameters current = parametersService.current();
            netMass = value.subtract(BigDecimal.valueOf(current.tareMassGrams()));
            if (netMass.signum() < 0) {
                grossMass = null;
                netMass = null;
                throw new IllegalStateException("Gross weight is below configured tare mass");
            }
            return grossMass;
        }
    }

    public void openHatch() {
        preparation("Opening spectrometer hatch", () -> {
            gateway.openHatch();
            return null;
        });
    }

    public void closeHatch() {
        preparation("Closing spectrometer hatch", () -> {
            gateway.closeHatch();
            return null;
        });
    }

    public void enableHighVoltage() {
        preparation("Enabling BOSON high voltage", () -> {
            gateway.enableHighVoltage();
            return null;
        });
    }

    public void disableHighVoltage() {
        preparation("Disabling BOSON high voltage", () -> {
            gateway.disableHighVoltage();
            return null;
        });
    }

    /** Starts the long Gamma-PRO acquisition outside the HTTP request thread. */
    public UUID startSpectrumAcquisition() {
        UUID acceptedId;
        MeasurementParameters acceptedParameters;
        executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.MANUAL);
        try {
            synchronized (this) {
                requireDataEntryState();
                if (sampleCode.isBlank()) {
                    throw new IllegalStateException("Enter or scan the sample code first");
                }
                if (grossMass == null || netMass == null) {
                    throw new IllegalStateException("Read a stable sample weight first");
                }
                state = ManualMeasurementState.PREPARING;
                currentOperation = "Checking spectrum preconditions";
                lastError = "";
            }
            gateway.verifySpectrumPreconditions();
            acceptedParameters = parametersService.current();
        } catch (RuntimeException exception) {
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.MANUAL);
            startPreparationFailed(exception);
            throw exception;
        }
        try {
            synchronized (this) {
                measurementId = UUID.randomUUID();
                acceptedId = measurementId;
                parameters = acceptedParameters;
                startedAt = clock.instant();
                estimatedFinishAt = startedAt.plus(
                        acceptedParameters.measuringTimeMinutes(), ChronoUnit.MINUTES
                );
                finishedAt = null;
                spectrumReference = "";
                resultSaved = false;
                state = ManualMeasurementState.ACQUIRING;
                currentOperation = "Acquiring spectrum";
                acquisitionTask = acquisitionExecutor.submit(
                        () -> acquire(acceptedId, acceptedParameters)
                );
            }
        } catch (RuntimeException exception) {
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.MANUAL);
            startPreparationFailed(exception);
            throw exception;
        }
        log.info("Manual measurement accepted: id={}, sample={}", acceptedId, sampleCode);
        return acceptedId;
    }

    public boolean stopSpectrumAcquisition() {
        Future<?> task;
        UUID id;
        synchronized (this) {
            if (state != ManualMeasurementState.ACQUIRING) return false;
            state = ManualMeasurementState.CANCELLING;
            currentOperation = "Stopping spectrum acquisition";
            task = acquisitionTask;
            id = measurementId;
        }
        if (task != null) task.cancel(true);
        try {
            gateway.stopSpectrumAcquisition();
            finish(id, ManualMeasurementState.CANCELLED, "", "");
        } catch (RuntimeException exception) {
            finish(id, ManualMeasurementState.FAILED, "", message(exception));
            throw exception;
        }
        return true;
    }

    public synchronized void reset() {
        if (active()) {
            throw new IllegalStateException("Stop the current manual operation first");
        }
        measurementId = null;
        state = ManualMeasurementState.IDLE;
        currentOperation = "";
        sampleCode = "";
        grossMass = null;
        netMass = null;
        parameters = null;
        startedAt = null;
        estimatedFinishAt = null;
        finishedAt = null;
        spectrumReference = "";
        lastError = "";
        resultSaved = false;
        acquisitionTask = null;
    }

    public synchronized ManualMeasurementSnapshot snapshot() {
        return new ManualMeasurementSnapshot(
                measurementId, state, currentOperation, sampleCode,
                grossMass, netMass, parameters, startedAt,
                estimatedFinishAt, finishedAt, spectrumReference, lastError
        );
    }

    private void acquire(UUID id, MeasurementParameters runParameters) {
        try {
            String result = gateway.acquireSpectrum(runParameters);
            finish(id, ManualMeasurementState.COMPLETED, normalize(result), "");
        } catch (RuntimeException exception) {
            synchronized (this) {
                if (measurementId == null || !measurementId.equals(id)
                        || state == ManualMeasurementState.CANCELLING
                        || state == ManualMeasurementState.CANCELLED) {
                    return;
                }
            }
            finish(id, ManualMeasurementState.FAILED, "", message(exception));
        }
    }

    private <T> T preparation(String operation, Callable<T> command) {
        ManualMeasurementState previousState = null;
        executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.MANUAL);
        try {
            synchronized (this) {
                requireEditable();
                previousState = state;
                state = ManualMeasurementState.PREPARING;
                currentOperation = operation;
                lastError = "";
            }
            T value = command.call();
            synchronized (this) {
                state = previousState;
                currentOperation = "";
            }
            return value;
        } catch (RuntimeException exception) {
            preparationFailed(
                    exception,
                    previousState == null ? ManualMeasurementState.IDLE : previousState
            );
            throw exception;
        } catch (Exception exception) {
            IllegalStateException failure = new IllegalStateException(operation + " failed", exception);
            preparationFailed(
                    failure,
                    previousState == null ? ManualMeasurementState.IDLE : previousState
            );
            throw failure;
        } finally {
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.MANUAL);
        }
    }

    private synchronized void preparationFailed(
            RuntimeException exception,
            ManualMeasurementState previousState
    ) {
        state = previousState;
        currentOperation = "";
        lastError = message(exception);
    }

    private synchronized void startPreparationFailed(RuntimeException exception) {
        if (state == ManualMeasurementState.PREPARING
                || state == ManualMeasurementState.ACQUIRING) {
            state = ManualMeasurementState.IDLE;
            currentOperation = "";
            measurementId = null;
            parameters = null;
            startedAt = null;
            estimatedFinishAt = null;
            finishedAt = null;
            acquisitionTask = null;
        }
        lastError = message(exception);
    }

    private void finish(
            UUID id,
            ManualMeasurementState terminalState,
            String spectrum,
            String error
    ) {
        ManualMeasurementResult result;
        synchronized (this) {
            if (!Objects.equals(measurementId, id) || resultSaved
                    || terminal()) return;
            finishedAt = clock.instant();
            spectrumReference = normalize(spectrum);
            lastError = normalize(error);
            result = new ManualMeasurementResult(
                    measurementId, sampleCode, grossMass, netMass, parameters,
                    startedAt, finishedAt, terminalState,
                    spectrumReference, lastError
            );
            resultSaved = true;
            state = ManualMeasurementState.PREPARING;
            currentOperation = "Saving measurement result";
        }
        try {
            repository.insert(result);
            synchronized (this) {
                state = terminalState;
                currentOperation = "";
            }
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.MANUAL);
            log.info("Manual measurement finished: id={}, state={}", id, terminalState);
        } catch (RuntimeException exception) {
            synchronized (this) {
                resultSaved = false;
                state = ManualMeasurementState.FAILED;
                lastError = "Cannot save measurement result: " + message(exception);
            }
            log.error("Cannot save manual measurement result: id={}", id, exception);
            executionCoordinator.release(MeasurementExecutionCoordinator.Owner.MANUAL);
        }
    }

    private void requireEditable() {
        if (active()) {
            throw new IllegalStateException("Manual operation is already active");
        }
    }

    private void requireDataEntryState() {
        if (state != ManualMeasurementState.IDLE) {
            throw new IllegalStateException(
                    "Start a new manual measurement before changing sample data"
            );
        }
    }

    private boolean active() {
        return state == ManualMeasurementState.PREPARING
                || state == ManualMeasurementState.ACQUIRING
                || state == ManualMeasurementState.CANCELLING;
    }

    private boolean terminal() {
        return state == ManualMeasurementState.COMPLETED
                || state == ManualMeasurementState.FAILED
                || state == ManualMeasurementState.CANCELLED;
    }

    @PreDestroy
    void shutdown() {
        Future<?> task;
        synchronized (this) { task = acquisitionTask; }
        if (task != null) task.cancel(true);
        executionCoordinator.release(MeasurementExecutionCoordinator.Owner.MANUAL);
        acquisitionExecutor.shutdownNow();
    }

    private static String normalizeCode(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Sample code must not be blank");
        }
        if (normalized.length() > 256) {
            throw new IllegalArgumentException("Sample code must not exceed 256 characters");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String message(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
