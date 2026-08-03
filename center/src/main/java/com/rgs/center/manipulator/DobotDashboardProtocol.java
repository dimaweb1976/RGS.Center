package com.rgs.center.manipulator;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.nio.charset.StandardCharsets;

/** Encoder and decoder for Dobot Dashboard ASCII commands on TCP port 29999. */
public final class DobotDashboardProtocol {

    /** Validates and encodes one complete command, for example GetPose(). */
    public byte[] encode(String command) {
        if (command == null || command.isBlank()
                || !command.endsWith(")")
                || command.indexOf('\r') >= 0
                || command.indexOf('\n') >= 0
                || command.indexOf(';') >= 0) {
            throw new IllegalArgumentException(
                    "Dobot command must be one ASCII expression ending with ')'"
            );
        }
        byte[] bytes = command.getBytes(StandardCharsets.US_ASCII);
        if (!command.equals(new String(bytes, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Dobot command must contain ASCII only");
        }
        return bytes;
    }

    /** Parses one semicolon-terminated Dashboard response. */
    public DobotCommandResponse parse(byte[] frame) {
        String raw = new String(frame, StandardCharsets.US_ASCII).trim();
        if (!raw.endsWith(";")) {
            throw new PeripheralCommunicationException(
                    "Dobot response has no ';' terminator"
            );
        }
        int firstComma = raw.indexOf(',');
        int valuesStart = raw.indexOf("{", firstComma);
        int valuesEnd = raw.indexOf("},", valuesStart);
        if (firstComma < 1 || valuesStart != firstComma + 1 || valuesEnd < 0) {
            throw new PeripheralCommunicationException(
                    "Malformed Dobot Dashboard response: " + raw
            );
        }
        try {
            int errorId = Integer.parseInt(raw.substring(0, firstComma).trim());
            String values = raw.substring(valuesStart + 1, valuesEnd).trim();
            String echoed = raw.substring(valuesEnd + 2, raw.length() - 1).trim();
            return new DobotCommandResponse(errorId, values, echoed, raw);
        } catch (NumberFormatException exception) {
            throw new PeripheralCommunicationException(
                    "Invalid Dobot ErrorID in response: " + raw,
                    exception
            );
        }
    }
}
