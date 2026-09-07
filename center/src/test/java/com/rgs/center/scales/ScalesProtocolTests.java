package com.rgs.center.scales;

import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalesProtocolTests {

    @Test
    void createsImmediateMassCommandWithCrLf() {
        assertArrayEquals(
                bytes("SI\r\n"),
                ScalesProtocol.immediateMassCommand()
        );
    }

    @Test
    void parsesStableZeroInGrams() {
        ScalesReading reading = ScalesProtocol.parseImmediateMass(
                frame(' ', ' ', "0.000", "g")
        );

        assertEquals(new BigDecimal("0.000"), reading.mass());
        assertEquals(ScalesStability.STABLE, reading.stability());
        assertTrue(reading.isZeroGrams());
    }

    @Test
    void parsesNegativeUnstableMass() {
        ScalesReading reading = ScalesProtocol.parseImmediateMass(
                frame('?', '-', "18.5", "g")
        );

        assertEquals(new BigDecimal("-18.5"), reading.mass());
        assertEquals(ScalesStability.UNSTABLE, reading.stability());
        assertFalse(reading.isZeroGrams());
    }

    @Test
    void normalizesKilogramsToGrams() {
        ScalesReading reading = ScalesProtocol.parseImmediateMass(
                frame(' ', ' ', "1.250", "kg")
        );

        assertEquals(new BigDecimal("1250.000"), reading.massGrams());
    }

    @Test
    void rejectsUnavailableAndMalformedResponses() {
        assertThrows(
                PeripheralCommunicationException.class,
                () -> ScalesProtocol.parseImmediateMass(bytes("SI_I"))
        );
        assertThrows(
                PeripheralCommunicationException.class,
                () -> ScalesProtocol.parseImmediateMass(bytes("SI broken"))
        );
        assertThrows(
                PeripheralCommunicationException.class,
                () -> ScalesProtocol.parseImmediateMass(
                        frame(' ', ' ', "1.0", "lb")
                )
        );
    }

    static byte[] frame(
            char stability,
            char sign,
            String mass,
            String unit
    ) {
        String value = "SI %c %c%9s %-3s".formatted(
                stability,
                sign,
                mass,
                unit
        );
        assertEquals(19, value.length());
        return bytes(value);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
