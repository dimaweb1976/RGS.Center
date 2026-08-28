package com.rgs.center.shp;

import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ShpStatusEvaluatorTests {

    private final ShpStatusEvaluator evaluator =
            new ShpStatusEvaluator(mock(SettingsService.class));

    @Test
    void valuesStrictlyBelowThresholdsAreReady() {
        ShpStatusView view = evaluator.evaluate(
                true,
                measurements(104.999, 49.999),
                105.0,
                50.0
        );

        assertEquals(ShpReadinessStatus.READY, view.readiness());
        assertEquals(104.999, view.chargeOutputVoltage());
        assertEquals(49.999, view.leakageCurrent());
    }

    @Test
    void equalityWithEitherThresholdIsAlert() {
        assertEquals(
                ShpReadinessStatus.ALERT,
                evaluator.evaluate(
                        true,
                        measurements(105.0, 49.999),
                        105.0,
                        50.0
                ).readiness()
        );
        assertEquals(
                ShpReadinessStatus.ALERT,
                evaluator.evaluate(
                        true,
                        measurements(104.999, 50.0),
                        105.0,
                        50.0
                ).readiness()
        );
    }

    @Test
    void exceededValueIsAlert() {
        assertEquals(
                ShpReadinessStatus.ALERT,
                evaluator.evaluate(
                        true,
                        measurements(106.0, 10.0),
                        105.0,
                        50.0
                ).readiness()
        );
    }

    @Test
    void disconnectedOrMissingMeasurementsHaveNoData() {
        assertEquals(
                ShpReadinessStatus.NO_DATA,
                evaluator.evaluate(
                        false,
                        measurements(10.0, 10.0),
                        105.0,
                        50.0
                ).readiness()
        );
        assertEquals(
                ShpReadinessStatus.NO_DATA,
                evaluator.evaluate(true, null, 105.0, 50.0).readiness()
        );
    }

    @Test
    void nonFiniteMeasurementHasNoData() {
        assertEquals(
                ShpReadinessStatus.NO_DATA,
                evaluator.evaluate(
                        true,
                        measurements(Double.NaN, 10.0),
                        105.0,
                        50.0
                ).readiness()
        );
    }

    private ShpMeasurements measurements(
            double chargeOutputVoltage,
            double leakageCurrent
    ) {
        return new ShpMeasurements(
                2500.0,
                0.0,
                -180.0,
                chargeOutputVoltage,
                leakageCurrent,
                0.0,
                25.0,
                40.0,
                750.0,
                1L
        );
    }
}
