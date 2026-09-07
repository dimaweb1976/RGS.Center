package com.rgs.center.scales;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Encoder and strict parser for the RADWAG character protocol. */
public final class ScalesProtocol {

    private static final byte[] IMMEDIATE_MASS_COMMAND =
            "SI\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final int MASS_FRAME_LENGTH_WITHOUT_CR_LF = 19;

    private ScalesProtocol() {
    }

    /** Command requesting an immediate result in the basic measuring unit. */
    public static byte[] immediateMassCommand() {
        return IMMEDIATE_MASS_COMMAND.clone();
    }

    /** Parses a frame after {@code LineFrameDecoder} has removed CR/LF. */
    public static ScalesReading parseImmediateMass(byte[] frame) {
        if (frame == null) {
            throw new PeripheralCommunicationException(
                    "Scales response must not be null"
            );
        }
        for (byte value : frame) {
            if (Byte.toUnsignedInt(value) > 0x7F) {
                throw new PeripheralCommunicationException(
                        "Scales response is not US-ASCII"
                );
            }
        }
        String value = new String(frame, StandardCharsets.US_ASCII);
        if ("SI_I".equals(value)) {
            throw new PeripheralCommunicationException(
                    "Scales measurement is not accessible at this moment"
            );
        }
        if (value.length() != MASS_FRAME_LENGTH_WITHOUT_CR_LF) {
            throw new PeripheralCommunicationException(
                    "Unexpected scales frame length: " + value.length()
            );
        }
        if (!value.startsWith("SI")
                || value.charAt(2) != ' '
                || value.charAt(4) != ' '
                || value.charAt(15) != ' ') {
            throw new PeripheralCommunicationException(
                    "Invalid scales mass frame layout"
            );
        }

        ScalesStability stability = ScalesStability.fromMarker(value.charAt(3));
        char sign = value.charAt(5);
        if (sign != ' ' && sign != '-') {
            throw new PeripheralCommunicationException(
                    "Invalid scales mass sign: " + sign
            );
        }
        String numericText = value.substring(6, 15).trim();
        if (!numericText.matches("(?:\\d+(?:\\.\\d*)?|\\.\\d+)")) {
            throw new PeripheralCommunicationException(
                    "Invalid scales mass value: " + numericText
            );
        }
        BigDecimal mass;
        try {
            mass = new BigDecimal((sign == '-' ? "-" : "") + numericText);
        } catch (NumberFormatException exception) {
            throw new PeripheralCommunicationException(
                    "Invalid scales mass value: " + numericText,
                    exception
            );
        }
        String unit = value.substring(16, 19).trim();
        if (unit.isEmpty()) {
            throw new PeripheralCommunicationException(
                    "Scales response contains no mass unit"
            );
        }
        ScalesReading reading = new ScalesReading(
                mass,
                unit,
                stability,
                Instant.now().getEpochSecond()
        );
        // Validate that the returned unit can be normalized for this page.
        reading.massGrams();
        return reading;
    }
}
