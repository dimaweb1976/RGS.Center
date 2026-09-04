package com.rgs.center.manipulator;

import com.rgs.center.peripheral.ByteRingBuffer;
import com.rgs.center.peripheral.PeripheralFrameDecoder;

/**
 * Восстанавливает границы 1440-байтовых сообщений Dobot в непрерывном
 * TCP-потоке по длине сообщения и документированному TestValue.
 */
public final class DobotFeedbackFrameDecoder
        implements PeripheralFrameDecoder {

    /** Little-endian представление unsigned short 1440 (0x05A0). */
    static final byte[] START_MARKER = {(byte) 0xA0, 0x05};

    /** Little-endian представление 0x0123456789ABCDEF. */
    static final byte[] TEST_MARKER = {
            (byte) 0xEF, (byte) 0xCD, (byte) 0xAB, (byte) 0x89,
            0x67, 0x45, 0x23, 0x01
    };

    static final int TEST_MARKER_OFFSET = 48;

    /** {@inheritDoc} */
    @Override
    public byte[] pollFrame(ByteRingBuffer buffer) {
        return buffer.pollSynchronizedFrame(
                DobotFeedbackParser.FRAME_LENGTH,
                START_MARKER,
                TEST_MARKER_OFFSET,
                TEST_MARKER
        );
    }
}
