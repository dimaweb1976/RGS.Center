package com.rgs.center.measurement.manual;

import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.OperationalState;
import com.rgs.center.dashboard.model.TechnicalState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionManualMeasurementDeviceGatewayTests {

    @Test
    void unstableConnectedScalesCanBeAskedForFreshStableWeight() {
        assertThatCode(() -> ProductionManualMeasurementDeviceGateway
                .requireScalesReadable(scales(TechnicalState.BUSY)))
                .doesNotThrowAnyException();
    }

    @Test
    void scalesAlertStillBlocksManualWeightCommand() {
        assertThatThrownBy(() -> ProductionManualMeasurementDeviceGateway
                .requireScalesReadable(scales(TechnicalState.ALERT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Scales technical status is ALERT");
    }

    private static DeviceHealth scales(TechnicalState technical) {
        return new DeviceHealth(
                "scales",
                "Scales",
                "COM7",
                true,
                ConnectionState.ONLINE,
                technical,
                OperationalState.IDLE,
                Instant.parse("2026-09-14T07:31:03Z"),
                false,
                List.of(),
                ""
        );
    }
}
