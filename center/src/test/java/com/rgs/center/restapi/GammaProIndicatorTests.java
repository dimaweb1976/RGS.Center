package com.rgs.center.restapi;

import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.GammaProEquipmentSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.TechnicalState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GammaProIndicatorTests {
    private static final Instant NOW = Instant.parse("2026-09-16T10:00:00Z");

    @Test
    void nullSnapshotIsUnknown() {
        assertEquals("UNKNOWN", HeadersIndicators.resolveGammaProStatus(null, NOW));
    }

    @Test
    void noAttemptIsNotOffline() {
        assertEquals("UNKNOWN", status(ConnectionState.OFFLINE, false,
                false, true, false, false, null));
    }

    @Test
    void failedTcpAttemptIsOffline() {
        assertEquals("OFFLINE", status(ConnectionState.OFFLINE, true,
                true, false, false, false, null));
    }

    @Test
    void pendingConnectionIsUnknown() {
        assertEquals("UNKNOWN", status(ConnectionState.CONNECTING, true,
                false, true, false, false, null));
    }

    @Test
    void confirmedIdleHeartbeatIsOnline() {
        assertEquals("ONLINE", status(ConnectionState.ONLINE, true,
                true, false, false, false, null));
    }

    @Test
    void staleHeartbeatIsUnknown() {
        assertEquals("UNKNOWN", status(ConnectionState.ONLINE, true,
                false, false, false, false, null));
    }

    @Test
    void responseTimeoutWithOpenSocketIsUnknown() {
        assertEquals("UNKNOWN", status(ConnectionState.ONLINE, true,
                true, true, false, false, null));
    }

    @Test
    void manualCommandIsInProgress() {
        assertEquals("IN_PROGRESS", status(ConnectionState.ONLINE, true,
                true, false, true, false, null));
    }

    @Test
    void heartbeatAloneDoesNotProduceInProgress() {
        assertEquals("ONLINE", status(ConnectionState.ONLINE, true,
                true, false, false, false, null));
    }

    @Test
    void longMeasurementDoesNotNeedIdleHeartbeat() {
        assertEquals("IN_PROGRESS", status(ConnectionState.ONLINE, true,
                false, false, false, true, NOW.plusSeconds(120)));
    }

    @Test
    void measurementDeadlineBoundsInProgress() {
        assertEquals("UNKNOWN", status(ConnectionState.ONLINE, true,
                true, false, false, true, NOW));
    }

    private String status(ConnectionState connection, boolean attempted,
            boolean fresh, boolean uncertain, boolean active,
            boolean running, Instant deadline) {
        DeviceHealth health = new DeviceHealth("gamma-pro", "GammaPRO",
                "127.0.0.1:37569", true, connection, TechnicalState.READY,
                running ? OperationalState.RUNNING : OperationalState.IDLE,
                NOW.minusSeconds(60), true, List.of(), "");
        var gamma = new GammaProEquipmentSnapshot(health, "127.0.0.1", 37569,
                running ? GammaProEquipmentSnapshot.SpectrumAcquisitionState.RUNNING
                        : GammaProEquipmentSnapshot.SpectrumAcquisitionState.IDLE,
                "BOSON", "Ch1", 120, List.of(), "", "", attempted,
                fresh ? NOW.minusSeconds(1) : NOW.minusSeconds(60), fresh,
                active, uncertain, deadline);
        return HeadersIndicators.resolveGammaProStatus(gamma, NOW);
    }
}
