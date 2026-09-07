package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentDataServiceTests {

    private CurrentDataService service;

    @BeforeEach
    void setUp() {
        service = new CurrentDataService();
        service.initializeDevices();
    }

    @Test
    void initializesEveryDeviceAsNotPinged() {
        for (DeviceCode device : DeviceCode.values()) {
            assertFalse(service.getValue(
                    device,
                    CurrentDataService.PINGED,
                    Boolean.class
            ));
            assertEquals(0L, service.getValue(
                    device,
                    CurrentDataService.INTERACTION_LASTDATE_IN,
                    Long.class
            ));
        }
    }

    @Test
    void storesDeviceParameter() {
        service.setValue(DeviceCode.BOSON, "status", "READY");

        assertEquals(
                "READY",
                service.getValue(DeviceCode.BOSON, "status", String.class)
        );
    }

    @Test
    void atomicallyMergesValuesFromOneDeviceSnapshot() {
        service.setValues(
                DeviceCode.ROBOTIC_MANIPULATOR,
                Map.of("x", 10.5, "y", 20.5)
        );

        Map<String, Object> snapshot = service.getDeviceData(
                DeviceCode.ROBOTIC_MANIPULATOR
        );
        assertEquals(10.5, snapshot.get("x"));
        assertEquals(20.5, snapshot.get("y"));
        assertEquals(false, snapshot.get(CurrentDataService.PINGED));
    }

    @Test
    void returnsReadOnlySnapshot() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> service.snapshot().put("other", null)
        );
    }
}
