package com.rgs.center.dashboard.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EquipmentSnapshotTests {

    @Test
    void aggregateSupportsSeveralCamerasAndHasStableDeviceOrder() {
        EquipmentSnapshot snapshot = sampleSnapshot(List.of(
                camera("camera-1", "Camera 1"),
                camera("camera-2", "Camera 2")
        ));

        assertEquals(2, snapshot.cameras().size());
        assertEquals(
                List.of(
                        "camera-1",
                        "camera-2",
                        "lenovo-pc",
                        "nicole",
                        "boson",
                        "robotic-manipulator",
                        "barcode-scanner",
                        "scales",
                        "shp",
                        "gamma-pro"
                ),
                snapshot.healthSummary().stream()
                        .map(DeviceHealth::code)
                        .toList()
        );
    }

    @Test
    void unknownMeasurementsAreRepresentedByNullInsteadOfSyntheticValues() {
        EquipmentSnapshot snapshot = sampleSnapshot(List.of(
                camera("camera", "Camera")
        ));

        assertNull(snapshot.nicole().currentNitrogenLevel());
        assertNull(snapshot.shp().ambientTemperatureCelsius());
        assertNull(snapshot.scales().mass());
        assertNull(snapshot.manipulator().pose().x());
    }

    static EquipmentSnapshot sampleSnapshot(
            List<CameraEquipmentSnapshot> cameras
    ) {
        DeviceHealth lenovo = ready("lenovo-pc", "LenovoPC");
        NicoleEquipmentSnapshot nicole = new NicoleEquipmentSnapshot(
                ready("nicole", "Nicole"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null
        );
        ShpEquipmentSnapshot shp = new ShpEquipmentSnapshot(
                ready("shp", "SHP"),
                null,
                null,
                null,
                null,
                null,
                null
        );
        ScalesEquipmentSnapshot scales = new ScalesEquipmentSnapshot(
                ready("scales", "Scales"),
                null,
                null,
                "g",
                ScalesEquipmentSnapshot.MeasurementStability.UNKNOWN,
                null
        );
        GammaProEquipmentSnapshot gammaPro = new GammaProEquipmentSnapshot(
                ready("gamma-pro", "Gamma-PRO"),
                "127.0.0.1",
                37569,
                GammaProEquipmentSnapshot.SpectrumAcquisitionState.IDLE,
                "",
                "",
                null,
                List.of(),
                "",
                ""
        );
        BosonEquipmentSnapshot boson = new BosonEquipmentSnapshot(
                ready("boson", "BOSON"),
                null,
                null,
                "",
                null,
                null,
                ""
        );
        BarcodeEquipmentSnapshot barcode = new BarcodeEquipmentSnapshot(
                ready("barcode-scanner", "Barcode scanner"),
                "COM7",
                9600,
                BarcodeEquipmentSnapshot.BarcodeOperationState.IDLE,
                0,
                3,
                null,
                null,
                "",
                "",
                ""
        );
        ManipulatorEquipmentSnapshot manipulator = new ManipulatorEquipmentSnapshot(
                ready("robotic-manipulator", "Robotic manipulator"),
                "192.168.5.1",
                29999,
                30004,
                new ManipulatorEquipmentSnapshot.Pose(
                        null, null, null, null, null, null
                ),
                new ManipulatorEquipmentSnapshot.Motion(
                        null, null, null, null, null, null
                ),
                new ManipulatorEquipmentSnapshot.Io("", ""),
                new ManipulatorEquipmentSnapshot.Hatch(
                        ManipulatorEquipmentSnapshot.HatchState.UNKNOWN,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        ""
                ),
                new ManipulatorEquipmentSnapshot.Safety(
                        "",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ),
                new ManipulatorEquipmentSnapshot.Telemetry(
                        "DISCONNECTED",
                        "DISCONNECTED",
                        null,
                        null,
                        null,
                        0,
                        0,
                        0
                )
        );

        return new EquipmentSnapshot(
                Instant.parse("2026-09-10T08:00:00Z"),
                cameras,
                lenovo,
                nicole,
                shp,
                scales,
                gammaPro,
                boson,
                barcode,
                manipulator
        );
    }

    static CameraEquipmentSnapshot camera(String code, String name) {
        return new CameraEquipmentSnapshot(
                ready(code, name),
                false,
                0,
                false,
                0,
                null,
                ""
        );
    }

    static DeviceHealth ready(String code, String name) {
        return new DeviceHealth(
                code,
                name,
                "test-endpoint",
                true,
                ConnectionState.ONLINE,
                TechnicalState.READY,
                OperationalState.IDLE,
                Instant.parse("2026-09-10T07:59:59Z"),
                false,
                List.of(),
                ""
        );
    }
}
