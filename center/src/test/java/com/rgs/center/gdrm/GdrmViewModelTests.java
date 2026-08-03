package com.rgs.center.gdrm;

import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GdrmViewModelTests {

    @Test
    void convertsProtocolUnitsForDisplay() {
        GdrmMeasurement measurement = new GdrmMeasurement(
                List.of(new GdrmChannelMeasurement(0.82698, 4.2109e-8, 0.105)),
                2.083e-8, 0.378, 2.083e-8, 661.0, "00000000", 1_785_740_217L
        );
        GdrmSnapshot snapshot = new GdrmSnapshot(
                "gdrm", "COM3", true, 9600, "X", "112873093", "2.302",
                0, 0, "", measurement, null,
                new PeripheralWorkerSnapshot(
                        "gdrm", PeripheralWorkerState.RUNNING, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, "")
        );

        GdrmViewModel view = GdrmViewModel.from(snapshot);

        assertEquals("20.83", view.averageDoseRate());
        assertEquals("37.8", view.averageStatisticalError());
        assertEquals("20.83", view.accumulatedDose());
        assertEquals("00:11:01", view.measurementTime());
        assertEquals("42.109", view.channels().getFirst().doseRate());
        assertEquals("10.5", view.channels().getFirst().statisticalError());
    }

    @Test
    void usesPlaceholdersBeforeFirstMeasurement() {
        GdrmSnapshot snapshot = new GdrmSnapshot(
                "gdrm", "COM3", false, 0, "X", "", "",
                0, 0, "", null, null, null
        );

        GdrmViewModel view = GdrmViewModel.from(snapshot);

        assertEquals("OFFLINE", view.connectionStatus());
        assertEquals("—", view.averageDoseRate());
        assertEquals("—", view.measurementTime());
        assertEquals(List.of(), view.channels());
    }
}
