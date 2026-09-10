package com.rgs.center.dashboard.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquipmentSnapshotImmutabilityTests {

    @Test
    void aggregateCopiesCameraListAndReturnsReadOnlyCollections() {
        List<CameraEquipmentSnapshot> cameras = new ArrayList<>();
        cameras.add(EquipmentSnapshotTests.camera("camera", "Camera"));
        EquipmentSnapshot snapshot = EquipmentSnapshotTests.sampleSnapshot(cameras);

        cameras.add(EquipmentSnapshotTests.camera("camera-2", "Camera 2"));

        assertEquals(1, snapshot.cameras().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.cameras().add(
                        EquipmentSnapshotTests.camera("camera-3", "Camera 3")
                )
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.healthSummary().clear()
        );
    }

    @Test
    void deviceSpecificListsAreDefensivelyCopied() {
        List<String> tracts = new ArrayList<>();
        tracts.add("TRACT-1");
        GammaProEquipmentSnapshot gammaPro = new GammaProEquipmentSnapshot(
                EquipmentSnapshotTests.ready("gamma-pro", "Gamma-PRO"),
                "127.0.0.1",
                37569,
                GammaProEquipmentSnapshot.SpectrumAcquisitionState.IDLE,
                "MCA",
                "Channel",
                60,
                tracts,
                "",
                ""
        );
        tracts.add("TRACT-2");

        List<String> warnings = new ArrayList<>();
        warnings.add("Warning 1");
        NicoleEquipmentSnapshot nicole = new NicoleEquipmentSnapshot(
                EquipmentSnapshotTests.ready("nicole", "Nicole"),
                true,
                true,
                true,
                true,
                false,
                1,
                2,
                3,
                4,
                5,
                null,
                0,
                1,
                warnings,
                null
        );
        warnings.add("Warning 2");

        assertEquals(List.of("TRACT-1"), gammaPro.tracts());
        assertEquals(List.of("Warning 1"), nicole.warnings());
        assertThrows(
                UnsupportedOperationException.class,
                () -> gammaPro.tracts().add("TRACT-3")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> nicole.warnings().clear()
        );
    }
}
