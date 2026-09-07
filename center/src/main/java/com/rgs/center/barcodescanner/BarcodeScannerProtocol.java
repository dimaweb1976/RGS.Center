package com.rgs.center.barcodescanner;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Byte-level serial protocol of the Honeywell Vuquest 3320 scanner. */
public final class BarcodeScannerProtocol {

    public static final byte ACK = 0x06;
    public static final byte ENQ = 0x05;
    public static final byte NAK = 0x15;
    private static final byte SYN = 0x16;
    private static final byte CR = 0x0D;
    private static final byte VOLATILE_STORAGE = '!';

    private BarcodeScannerProtocol() {
    }

    public static byte[] activateTrigger() {
        return new byte[]{SYN, 'T', CR};
    }

    public static byte[] deactivateTrigger() {
        return new byte[]{SYN, 'U', CR};
    }

    public static byte[] manualTriggerNormal() {
        return menu("PAPHHF");
    }

    public static byte[] manualIlluminationHigh() {
        return menu("PWRNOL150");
    }

    public static byte[] aimerOff() {
        return menu("SCNAIM0");
    }

    public static byte[] illuminationOn() {
        return menu("SCNLED1");
    }

    public static byte[] illuminationOff() {
        return menu("SCNLED0");
    }

    public static byte[] aimerQuery() {
        return menu("SCNAIM?");
    }

    /** Builds a volatile menu command: SYN M CR + ASCII body + !. */
    static byte[] menu(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Menu command must not be blank");
        }
        byte[] value = body.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream result = new ByteArrayOutputStream(value.length + 4);
        result.write(SYN);
        result.write('M');
        result.write(CR);
        result.writeBytes(value);
        result.write(VOLATILE_STORAGE);
        return result.toByteArray();
    }

    /** Parses an echoed menu response ending in the selected storage marker. */
    public static MenuResponse parseMenuResponse(byte[] frame) {
        if (frame == null || frame.length < 2) {
            throw new BarcodeScannerException("Empty scanner menu response");
        }
        byte terminator = frame[frame.length - 1];
        if (terminator != '!' && terminator != '.') {
            throw new BarcodeScannerException(
                    "Scanner menu response has no storage terminator"
            );
        }
        int statusIndex = -1;
        MenuResponseStatus status = null;
        for (int index = 0; index < frame.length - 1; index++) {
            status = MenuResponseStatus.fromByte(frame[index]);
            if (status != null) {
                statusIndex = index;
                break;
            }
        }
        if (statusIndex < 0) {
            throw new BarcodeScannerException(
                    "Scanner menu response has no ACK/ENQ/NAK status"
            );
        }
        String echoed = new String(
                Arrays.copyOfRange(frame, 0, statusIndex),
                StandardCharsets.US_ASCII
        );
        return new MenuResponse(echoed, status, (char) terminator);
    }

    public enum MenuResponseStatus {
        ACKNOWLEDGED,
        INVALID_COMMAND,
        INVALID_VALUE;

        private static MenuResponseStatus fromByte(byte value) {
            return switch (value) {
                case ACK -> ACKNOWLEDGED;
                case ENQ -> INVALID_COMMAND;
                case NAK -> INVALID_VALUE;
                default -> null;
            };
        }
    }

    public record MenuResponse(
            String echoedCommand,
            MenuResponseStatus status,
            char storage
    ) {
        public boolean acknowledged() {
            return status == MenuResponseStatus.ACKNOWLEDGED;
        }
    }
}
