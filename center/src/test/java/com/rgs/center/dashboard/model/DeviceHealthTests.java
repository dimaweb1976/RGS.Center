package com.rgs.center.dashboard.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeviceHealthTests {

    @Test
    void disabledDeviceRequiresAllNormalizedStatesToBeDisabled() {
        DeviceHealth health = new DeviceHealth(
                "nicole",
                "Nicole",
                "192.168.1.15:5000",
                false,
                ConnectionState.DISABLED,
                TechnicalState.DISABLED,
                OperationalState.DISABLED,
                null,
                false,
                List.of("Device polling is disabled"),
                ""
        );

        assertEquals(ConnectionState.DISABLED, health.connection());
        assertEquals(TechnicalState.DISABLED, health.technical());
        assertEquals(OperationalState.DISABLED, health.operation());

        assertThrows(IllegalArgumentException.class, () -> new DeviceHealth(
                "nicole",
                "Nicole",
                "192.168.1.15:5000",
                false,
                ConnectionState.OFFLINE,
                TechnicalState.DISABLED,
                OperationalState.DISABLED,
                null,
                false,
                List.of(),
                ""
        ));
    }

    @Test
    void enabledDeviceCannotUseDisabledNormalizedState() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceHealth(
                "shp",
                "SHP",
                "COM5",
                true,
                ConnectionState.ONLINE,
                TechnicalState.DISABLED,
                OperationalState.IDLE,
                Instant.parse("2026-09-10T08:00:00Z"),
                false,
                List.of(),
                ""
        ));
    }

    @Test
    void connectionAndTechnicalStatusRemainIndependent() {
        DeviceHealth health = new DeviceHealth(
                "nicole",
                "Nicole",
                "192.168.1.15:5000",
                true,
                ConnectionState.ONLINE,
                TechnicalState.ALERT,
                OperationalState.IDLE,
                Instant.parse("2026-09-10T08:00:00Z"),
                false,
                List.of("Cooler warning is active"),
                ""
        );

        assertEquals(ConnectionState.ONLINE, health.connection());
        assertEquals(TechnicalState.ALERT, health.technical());
    }

    @Test
    void reasonsAreDefensivelyCopied() {
        List<String> source = new ArrayList<>();
        source.add("No protocol data");

        DeviceHealth health = new DeviceHealth(
                "camera",
                "Camera",
                "192.168.1.13",
                true,
                ConnectionState.OFFLINE,
                TechnicalState.NO_DATA,
                OperationalState.UNKNOWN,
                null,
                true,
                source,
                "Connection refused"
        );
        source.add("Must not leak into snapshot");

        assertEquals(List.of("No protocol data"), health.reasons());
        assertThrows(
                UnsupportedOperationException.class,
                () -> health.reasons().add("Mutation")
        );
    }

    @Test
    void codeAndNameMustNotBeBlank() {
        assertThrows(IllegalArgumentException.class, () -> new DeviceHealth(
                " ",
                "Camera",
                "192.168.1.13",
                true,
                ConnectionState.OFFLINE,
                TechnicalState.NO_DATA,
                OperationalState.UNKNOWN,
                null,
                true,
                List.of(),
                ""
        ));
    }
}
