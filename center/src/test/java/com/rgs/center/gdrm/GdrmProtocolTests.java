package com.rgs.center.gdrm;

import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GdrmProtocolTests {

    @Test
    void commandAddsAddressAndCrLf() {
        assertArrayEquals(
                "Xss\r\n".getBytes(StandardCharsets.US_ASCII),
                GdrmProtocol.command("X", "ss")
        );
    }

    @Test
    void parsesSerialNumberParameter() {
        assertEquals(
                "123456789",
                GdrmProtocol.parseParameter(bytes("s:123456789"), 's')
        );
    }

    @Test
    void parsesFormatNineMeasurement() {
        GdrmMeasurement value = GdrmProtocol.parseMeasurement(bytes(
                String.join(
                        ";",
                        "1", "2e-9", "0.01",
                        "", "", "",
                        "", "", "",
                        "", "", "",
                        "2e-9", "0.02", "3e-6", "15", "00000000"
                )
        ));

        assertEquals(4, value.channels().size());
        assertEquals(1.0, value.channels().getFirst().countsPerSecond());
        assertEquals(2e-9, value.channels().getFirst().doseRateSvPerHour());
        assertNull(value.channels().get(1).countsPerSecond());
        assertEquals(2e-9, value.weightedDoseRateSvPerHour());
        assertEquals(3e-6, value.accumulatedDoseSv());
        assertEquals("00000000", value.deviceStatus());
    }

    @Test
    void parsesOperatingDataWithOptionalFields() {
        GdrmOperatingData value = GdrmProtocol.parseOperatingData(
                bytes("11.7;500.2;32.0;;;00000000")
        );

        assertEquals(11.7, value.operatingVoltageVolts());
        assertEquals(500.2, value.highVoltageVolts());
        assertEquals(32.0, value.temperatureCelsius());
        assertNull(value.relativeHumidityPercent());
        assertNull(value.airPressureHpa());
        assertEquals("00000000", value.deviceStatus());
    }

    @Test
    void rejectsMeasurementWithWrongFieldCount() {
        assertThrows(
                PeripheralCommunicationException.class,
                () -> GdrmProtocol.parseMeasurement(bytes("1;2;3"))
        );
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
