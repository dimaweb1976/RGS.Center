package com.rgs.center.barcodescanner;

import com.rgs.center.peripheral.ByteRingBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BarcodeScannerFrameDecoderTests {

    @Test
    void assemblesFragmentedMenuResponseThroughVolatileTerminator() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(64);
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        buffer.write(bytes("SCNAIM0"), 7);

        assertNull(decoder.pollFrame(buffer));

        byte[] tail = new byte[]{BarcodeScannerProtocol.ACK, '!'};
        buffer.write(tail, tail.length);
        assertArrayEquals(
                new byte[]{'S', 'C', 'N', 'A', 'I', 'M', '0', 0x06, '!'},
                decoder.pollFrame(buffer)
        );
    }

    @Test
    void assemblesCrLfTerminatedBarcodeInBarcodeMode() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(64);
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes("ABC-123\r\n");
        buffer.write(value, value.length);

        assertArrayEquals(bytes("ABC-123"), decoder.pollFrame(buffer));
        assertNull(decoder.pollFrame(buffer));
    }

    @Test
    void supportsEveryConfiguredLineSuffixWithoutReturningIt() {
        assertDecodes(BarcodeScannerDataSuffix.CR, "CR-CODE\r", "CR-CODE");
        assertDecodes(BarcodeScannerDataSuffix.LF, "LF-CODE\n", "LF-CODE");
        assertDecodes(
                BarcodeScannerDataSuffix.LF,
                "LF-ACCEPTS-CRLF\r\n",
                "LF-ACCEPTS-CRLF"
        );
        assertDecodes(
                BarcodeScannerDataSuffix.CRLF,
                "CRLF-CODE\r\n",
                "CRLF-CODE"
        );
    }

    @Test
    void assemblesConfiguredCrLfSplitAcrossSerialReads() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(
                64,
                BarcodeScannerDataSuffix.CRLF
        );
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] first = bytes("SPLIT-CODE\r");
        buffer.write(first, first.length);

        assertNull(decoder.pollFrame(buffer));

        byte[] second = bytes("\n");
        buffer.write(second, second.length);
        assertArrayEquals(bytes("SPLIT-CODE"), decoder.pollFrame(buffer));
    }

    @Test
    void decodesTwoCrLfTerminatedBarcodesFromOneRead() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(
                64,
                BarcodeScannerDataSuffix.CRLF
        );
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes("FIRST\r\nSECOND\r\n");
        buffer.write(value, value.length);

        assertArrayEquals(bytes("FIRST"), decoder.pollFrame(buffer));
        assertArrayEquals(bytes("SECOND"), decoder.pollFrame(buffer));
        assertNull(decoder.pollFrame(buffer));
    }

    @Test
    void completesUnterminatedBarcodeWhenSerialTransportBecomesIdle() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(64);
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes("332 -ENUS-QS");
        buffer.write(value, value.length);

        assertNull(decoder.pollFrame(buffer));
        assertArrayEquals(value, decoder.pollIdleFrame(buffer));
        assertNull(decoder.pollIdleFrame(buffer));
    }

    @Test
    void noneModeCompletesOnlyWhenSerialTransportBecomesIdle() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(
                64,
                BarcodeScannerDataSuffix.NONE
        );
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes("NO-SUFFIX");
        buffer.write(value, value.length);

        assertNull(decoder.pollFrame(buffer));
        assertArrayEquals(value, decoder.pollIdleFrame(buffer));
    }

    @Test
    void explicitCrModeDoesNotFallbackToIdleFraming() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(
                64,
                BarcodeScannerDataSuffix.CR
        );
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes("INCOMPLETE");
        buffer.write(value, value.length);

        assertNull(decoder.pollFrame(buffer));
        assertNull(decoder.pollIdleFrame(buffer));
    }

    @Test
    void doesNotFlushPartialMenuResponseWhenSerialTransportBecomesIdle() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(64);
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes("SCNAIM0");
        buffer.write(value, value.length);

        assertNull(decoder.pollIdleFrame(buffer));
        assertNull(decoder.pollFrame(buffer));
    }

    @Test
    void exposesStaleLineBeforeFollowingMenuResponse() {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(64);
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = new byte[]{
                'O', 'L', 'D', '\r', '\n',
                'S', 'C', 'N', 'A', 'I', 'M', '0', 0x06, '!'
        };
        buffer.write(value, value.length);

        assertArrayEquals(bytes("OLD"), decoder.pollFrame(buffer));
        assertArrayEquals(
                new byte[]{'S', 'C', 'N', 'A', 'I', 'M', '0', 0x06, '!'},
                decoder.pollFrame(buffer)
        );
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private void assertDecodes(
            BarcodeScannerDataSuffix suffix,
            String wireValue,
            String expected
    ) {
        BarcodeScannerFrameDecoder decoder = new BarcodeScannerFrameDecoder(
                64,
                suffix
        );
        decoder.expectBarcode();
        ByteRingBuffer buffer = new ByteRingBuffer(128);
        byte[] value = bytes(wireValue);
        buffer.write(value, value.length);

        assertArrayEquals(bytes(expected), decoder.pollFrame(buffer));
        assertNull(decoder.pollFrame(buffer));
    }
}
