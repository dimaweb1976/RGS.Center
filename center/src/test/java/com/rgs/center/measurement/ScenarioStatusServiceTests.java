package com.rgs.center.measurement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScenarioStatusServiceTests {

    @Test
    void startsNormalAndStoresEverySupportedStatus() {
        ScenarioStatusService service = new ScenarioStatusService();

        assertEquals(ScenarioStatus.NORMAL, service.currentStatus());

        for (ScenarioStatus status : ScenarioStatus.values()) {
            service.updateStatus(status, "test transition");
            assertEquals(status, service.currentStatus());
        }
    }

    @Test
    void exposesStableLabelsForWebTable() {
        assertEquals("NORMAL", ScenarioStatus.NORMAL.getLabel());
        assertEquals("EMERGENCY", ScenarioStatus.EMERGENCY.getLabel());
        assertEquals("PAUSED", ScenarioStatus.PAUSED.getLabel());
        assertEquals("ABORTING", ScenarioStatus.ABORTING.getLabel());
        assertEquals(
                "RECOVERY_REQUIRED",
                ScenarioStatus.RECOVERY_REQUIRED.getLabel()
        );
    }
}
