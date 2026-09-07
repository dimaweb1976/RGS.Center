package com.rgs.center.barcodescanner;

import com.rgs.center.peripheral.ByteRingBuffer;
import com.rgs.center.peripheral.PeripheralFrameDecoder;

import java.util.concurrent.atomic.AtomicReference;

/** Selects command-response or barcode framing for the scanner RX stream. */
final class BarcodeScannerFrameDecoder implements PeripheralFrameDecoder {

    private static final byte[] CR = {'\r'};
    private static final byte[] LF = {'\n'};
    private static final byte[] CRLF = {'\r', '\n'};

    private enum Mode {
        COMMAND_RESPONSE,
        BARCODE
    }

    private final int maxBarcodeLength;
    private final BarcodeScannerDataSuffix dataSuffix;
    private final AtomicReference<Mode> mode =
            new AtomicReference<>(Mode.COMMAND_RESPONSE);

    BarcodeScannerFrameDecoder(int maxBarcodeLength) {
        this(maxBarcodeLength, BarcodeScannerDataSuffix.AUTO);
    }

    BarcodeScannerFrameDecoder(
            int maxBarcodeLength,
            BarcodeScannerDataSuffix dataSuffix
    ) {
        if (maxBarcodeLength <= 0) {
            throw new IllegalArgumentException(
                    "Maximum barcode length must be positive"
            );
        }
        this.maxBarcodeLength = maxBarcodeLength;
        this.dataSuffix = java.util.Objects.requireNonNull(
                dataSuffix,
                "dataSuffix"
        );
    }

    void expectCommandResponse() {
        mode.set(Mode.COMMAND_RESPONSE);
    }

    void expectBarcode() {
        mode.set(Mode.BARCODE);
    }

    @Override
    public byte[] pollFrame(ByteRingBuffer buffer) {
        if (mode.get() == Mode.COMMAND_RESPONSE) {
            return buffer.pollLineOrTerminated((byte) '!', 512);
        }
        return switch (dataSuffix) {
            case AUTO -> buffer.pollLine(maxBarcodeLength);
            case CR -> buffer.pollLineEnding(CR, maxBarcodeLength);
            case LF -> buffer.pollLineEnding(LF, maxBarcodeLength);
            case CRLF -> buffer.pollLineEnding(CRLF, maxBarcodeLength);
            case NONE -> null;
        };
    }

    /**
     * Vuquest may send decoded data without CR/LF. A serial read timeout then
     * marks the end of the complete barcode received so far.
     */
    @Override
    public byte[] pollIdleFrame(ByteRingBuffer buffer) {
        if (mode.get() != Mode.BARCODE) {
            return null;
        }
        if (dataSuffix != BarcodeScannerDataSuffix.AUTO
                && dataSuffix != BarcodeScannerDataSuffix.NONE) {
            return pollFrame(buffer);
        }
        if (dataSuffix == BarcodeScannerDataSuffix.AUTO) {
            byte[] terminated = buffer.pollLine(maxBarcodeLength);
            if (terminated != null) {
                return terminated;
            }
        }
        int buffered = buffer.size();
        return buffered == 0 ? null : buffer.pollBytes(buffered);
    }
}
