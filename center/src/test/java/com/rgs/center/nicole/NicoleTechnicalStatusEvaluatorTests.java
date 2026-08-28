package com.rgs.center.nicole;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NicoleTechnicalStatusEvaluatorTests {

    private final NicoleTechnicalStatusEvaluator evaluator =
            new NicoleTechnicalStatusEvaluator();

    @Test
    void returnsReadyOnlyForStartedCoolerWithoutErrorsOrWarnings() {
        assertEquals(
                NicoleReadinessStatus.READY,
                evaluator.evaluate(snapshot(true, 0, 0))
        );
    }

    @Test
    void returnsAlertForStoppedCoolerErrorOrWarning() {
        assertEquals(
                NicoleReadinessStatus.ALERT,
                evaluator.evaluate(snapshot(false, 0, 0))
        );
        assertEquals(
                NicoleReadinessStatus.ALERT,
                evaluator.evaluate(snapshot(true, 3, 0))
        );
        assertEquals(
                NicoleReadinessStatus.ALERT,
                evaluator.evaluate(snapshot(true, 0, 1 << 10))
        );
    }

    @Test
    void explainsEveryActiveAlertCondition() {
        var reasons = evaluator.alertReasons(snapshot(false, 7, 1 << 10));

        assertTrue(reasons.contains("Cryocooler stopped"));
        assertTrue(reasons.contains("Cooler error code: 7"));
        assertTrue(reasons.contains("LN2 low level"));
    }

    private static NicoleSnapshot snapshot(
            boolean started,
            int error,
            int warnings
    ) {
        return new NicoleSnapshot(
                0,
                true,
                false,
                true,
                started,
                false,
                0,
                0,
                0,
                0,
                0,
                LocalDateTime.of(2026, 8, 28, 12, 0),
                error,
                warnings,
                Instant.now()
        );
    }
}
