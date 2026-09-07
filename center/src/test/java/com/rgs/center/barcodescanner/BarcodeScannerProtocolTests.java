package com.rgs.center.barcodescanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeScannerProtocolTests {

    @Test
    void encodesTriggerCommandsExactlyAsDocumented() {
        assertArrayEquals(
                new byte[]{0x16, 0x54, 0x0D},
                BarcodeScannerProtocol.activateTrigger()
        );
        assertArrayEquals(
                new byte[]{0x16, 0x55, 0x0D},
                BarcodeScannerProtocol.deactivateTrigger()
        );
    }

    @Test
    void encodesDocumentedIlluminationAndAimerCommandsAsVolatile() {
        assertArrayEquals(
                bytes(0x16, 'M', 0x0D, "PWRNOL150!"),
                BarcodeScannerProtocol.manualIlluminationHigh()
        );
        assertArrayEquals(
                bytes(0x16, 'M', 0x0D, "SCNAIM0!"),
                BarcodeScannerProtocol.aimerOff()
        );
        assertArrayEquals(
                bytes(0x16, 'M', 0x0D, "SCNLED1!"),
                BarcodeScannerProtocol.illuminationOn()
        );
        assertArrayEquals(
                bytes(0x16, 'M', 0x0D, "SCNLED0!"),
                BarcodeScannerProtocol.illuminationOff()
        );
    }

    @Test
    void parsesAcknowledgedAndRejectedMenuResponses() {
        var acknowledged = BarcodeScannerProtocol.parseMenuResponse(
                new byte[]{'S', 'C', 'N', 'L', 'E', 'D', '1', 0x06, '!'}
        );
        var rejected = BarcodeScannerProtocol.parseMenuResponse(
                new byte[]{'S', 'C', 'N', 'L', 'E', 'D', '9', 0x15, '!'}
        );

        assertTrue(acknowledged.acknowledged());
        assertEquals("SCNLED1", acknowledged.echoedCommand());
        assertEquals('!', acknowledged.storage());
        assertFalse(rejected.acknowledged());
        assertEquals(
                BarcodeScannerProtocol.MenuResponseStatus.INVALID_VALUE,
                rejected.status()
        );
    }

    private byte[] bytes(int first, char second, int third, String suffix) {
        byte[] text = suffix.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] result = new byte[text.length + 3];
        result[0] = (byte) first;
        result[1] = (byte) second;
        result[2] = (byte) third;
        System.arraycopy(text, 0, result, 3, text.length);
        return result;
    }
}
