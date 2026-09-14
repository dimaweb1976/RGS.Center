package com.rgs.center.measurement.safety;

import com.rgs.center.measurement.ScenarioStatus;
import com.rgs.center.measurement.ScenarioStatusService;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Thread-safe store for the current workflow incident and its operator audit.
 * Repeated reports with the same code are coalesced instead of flooding logs.
 */
@Service
public class ScenarioIncidentService {

    private static final Logger log = LoggerFactory.getLogger(
            ScenarioIncidentService.class
    );
    private static final int MAX_EVENTS = 200;

    private final ScenarioStatusService scenarioStatusService;
    private final ScenarioIncidentRepository repository;
    private final Clock clock;
    private final ArrayDeque<ScenarioIncidentEvent> events = new ArrayDeque<>();
    private ScenarioIncidentSnapshot current = ScenarioIncidentSnapshot.none();
    private boolean persistenceAvailable;

    public ScenarioIncidentService(ScenarioStatusService scenarioStatusService) {
        this(scenarioStatusService, null, Clock.systemUTC());
    }

    ScenarioIncidentService(ScenarioStatusService scenarioStatusService, Clock clock) {
        this(scenarioStatusService, null, clock);
    }

    @Autowired
    public ScenarioIncidentService(
            ScenarioStatusService scenarioStatusService,
            ScenarioIncidentRepository repository
    ) {
        this(scenarioStatusService, repository, Clock.systemUTC());
    }

    ScenarioIncidentService(
            ScenarioStatusService scenarioStatusService,
            ScenarioIncidentRepository repository,
            Clock clock
    ) {
        this.scenarioStatusService = Objects.requireNonNull(
                scenarioStatusService, "scenarioStatusService"
        );
        this.repository = repository;
        this.clock = Objects.requireNonNull(clock, "clock");
        persistenceAvailable = repository != null;
        if (repository != null) {
            try {
                repository.findLatestUnresolved().ifPresent(value -> {
                    current = value;
                    scenarioStatusService.updateStatus(
                            value.status() == ScenarioIncidentStatus.ACTIVE
                                    ? ScenarioStatus.EMERGENCY
                                    : ScenarioStatus.RECOVERY_REQUIRED,
                            "unresolved incident restored after startup"
                    );
                });
            } catch (RuntimeException exception) {
                persistenceAvailable = false;
                log.warn(
                        "Scenario incident persistence is unavailable; apply "
                                + "dbscripts/090.rgscenter_scenario_incidents.sql "
                                + "and restart RGSCenter: {}",
                        exception.getMessage()
                );
            }
        }
    }

    /** Raises or returns the unresolved incident with the same stable code. */
    public synchronized ScenarioIncidentSnapshot raise(
            ScenarioIncidentSeverity severity,
            String source,
            String code,
            String message,
            UUID runId,
            Integer objectNumber,
            MeasurementWorkflowStep step
    ) {
        requireText(source, "source");
        requireText(code, "code");
        requireText(message, "message");
        Objects.requireNonNull(severity, "severity");
        if (current.present()
                && current.status() != ScenarioIncidentStatus.CLEARED
                && current.code().equals(code)) {
            return current;
        }
        if (current.present() && current.status() != ScenarioIncidentStatus.CLEARED) {
            throw new IllegalStateException(
                    "Resolve incident " + current.id() + " before raising another one"
            );
        }

        Instant now = clock.instant();
        current = new ScenarioIncidentSnapshot(
                UUID.randomUUID(), severity, ScenarioIncidentStatus.ACTIVE,
                source.trim(), code.trim(), message.trim(), runId, objectNumber,
                step, now, "", null, "", null
        );
        persist(() -> repository.insert(current));
        audit(current.id(), "RAISED", "system", current.message());
        if (severity == ScenarioIncidentSeverity.ALERT
                || severity == ScenarioIncidentSeverity.EMERGENCY) {
            scenarioStatusService.updateStatus(ScenarioStatus.EMERGENCY, message);
        }
        log.error(
                "Scenario incident raised: id={}, severity={}, source={}, code={}, message={}",
                current.id(), severity, current.source(), current.code(), current.message()
        );
        return current;
    }

    /** Records that an operator has seen and accepted responsibility for the event. */
    public synchronized ScenarioIncidentSnapshot acknowledge(String actor) {
        requireActor(actor);
        if (!current.present() || current.status() != ScenarioIncidentStatus.ACTIVE) {
            throw new IllegalStateException("There is no active incident to acknowledge");
        }
        Instant now = clock.instant();
        current = new ScenarioIncidentSnapshot(
                current.id(), current.severity(), ScenarioIncidentStatus.ACKNOWLEDGED,
                current.source(), current.code(), current.message(), current.runId(),
                current.objectNumber(), current.step(), current.raisedAt(), actor.trim(),
                now, "", null
        );
        persist(() -> repository.acknowledge(current));
        audit(current.id(), "ACKNOWLEDGED", actor, current.message());
        scenarioStatusService.updateStatus(
                ScenarioStatus.RECOVERY_REQUIRED,
                "incident acknowledged by " + actor
        );
        log.info("Scenario incident acknowledged: id={}, actor={}", current.id(), actor);
        return current;
    }

    /** Clears an acknowledged incident after the caller has revalidated equipment. */
    public synchronized ScenarioIncidentSnapshot reset(String actor) {
        requireActor(actor);
        if (!current.present()
                || current.status() != ScenarioIncidentStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    "The incident must be acknowledged before recovery reset"
            );
        }
        Instant now = clock.instant();
        current = new ScenarioIncidentSnapshot(
                current.id(), current.severity(), ScenarioIncidentStatus.CLEARED,
                current.source(), current.code(), current.message(), current.runId(),
                current.objectNumber(), current.step(), current.raisedAt(),
                current.acknowledgedBy(), current.acknowledgedAt(), actor.trim(), now
        );
        persist(() -> repository.clear(current));
        audit(current.id(), "CLEARED", actor, "Equipment recovery confirmed");
        scenarioStatusService.updateStatus(
                ScenarioStatus.NORMAL,
                "incident cleared by " + actor
        );
        log.info("Scenario incident cleared: id={}, actor={}", current.id(), actor);
        return current;
    }

    public synchronized ScenarioIncidentSnapshot snapshot() {
        return current;
    }

    public synchronized List<ScenarioIncidentEvent> events() {
        return List.copyOf(events);
    }

    public synchronized boolean blocksWorkflowStart() {
        return current.present() && current.status() != ScenarioIncidentStatus.CLEARED;
    }

    /** @return whether incident audit is currently backed by PostgreSQL */
    public synchronized boolean persistenceAvailable() {
        return persistenceAvailable;
    }

    private void persist(Runnable action) {
        if (!persistenceAvailable || repository == null) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException exception) {
            persistenceAvailable = false;
            log.error(
                    "Scenario incident was kept in memory but not saved to PostgreSQL: {}",
                    exception.getMessage()
            );
        }
    }

    private void audit(UUID incidentId, String action, String actor, String details) {
        while (events.size() >= MAX_EVENTS) {
            events.removeFirst();
        }
        events.addLast(new ScenarioIncidentEvent(
                clock.instant(), incidentId, action, actor, details
        ));
    }

    private static void requireActor(String actor) {
        requireText(actor, "actor");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
