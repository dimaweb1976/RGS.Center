package com.rgs.center.manipulator;

import com.rgs.center.peripheral.ByteRingBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DobotFeedbackFrameDecoderTests {

    private final DobotFeedbackFrameDecoder decoder =
            new DobotFeedbackFrameDecoder();
    private final ByteRingBuffer buffer = new ByteRingBuffer(8192);

    @Test
    void waitsForFrameSplitAcrossArbitraryTcpReads() {
        byte[] frame = validFrame((byte) 0x31);

        write(Arrays.copyOfRange(frame, 0, 1));
        assertThat(decoder.pollFrame(buffer)).isNull();
        write(Arrays.copyOfRange(frame, 1, 55));
        assertThat(decoder.pollFrame(buffer)).isNull();
        write(Arrays.copyOfRange(frame, 55, frame.length));

        assertThat(decoder.pollFrame(buffer)).containsExactly(frame);
    }

    @Test
    void returnsTwoFramesReceivedInOneTcpRead() {
        byte[] first = validFrame((byte) 0x11);
        byte[] second = validFrame((byte) 0x22);
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);

        write(combined);

        assertThat(decoder.pollFrame(buffer)).containsExactly(first);
        assertThat(decoder.pollFrame(buffer)).containsExactly(second);
    }

    @Test
    void discardsNoiseAndFalseLengthMarkerBeforeValidFrame() {
        byte[] falseCandidate = validFrame((byte) 0x44);
        falseCandidate[DobotFeedbackFrameDecoder.TEST_MARKER_OFFSET] = 0;
        byte[] valid = validFrame((byte) 0x55);
        byte[] stream = new byte[3 + falseCandidate.length + valid.length];
        stream[0] = 0x01;
        stream[1] = 0x02;
        stream[2] = 0x03;
        System.arraycopy(falseCandidate, 0, stream, 3, falseCandidate.length);
        System.arraycopy(valid, 0, stream, 3 + falseCandidate.length, valid.length);

        write(stream);

        assertThat(decoder.pollFrame(buffer)).containsExactly(valid);
    }

    @Test
    void retainsPartialStartMarkerAtEndOfCurrentRead() {
        write(new byte[]{0x10, 0x20, (byte) 0xA0});

        assertThat(decoder.pollFrame(buffer)).isNull();
        assertThat(buffer.size()).isEqualTo(1);

        byte[] frame = validFrame((byte) 0x66);
        write(Arrays.copyOfRange(frame, 1, frame.length));
        assertThat(decoder.pollFrame(buffer)).containsExactly(frame);
    }

    private static byte[] validFrame(byte payloadMarker) {
        byte[] frame = new byte[DobotFeedbackParser.FRAME_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) DobotFeedbackParser.FRAME_LENGTH);
        buffer.putLong(48, DobotFeedbackParser.TEST_VALUE);
        frame[100] = payloadMarker;
        return frame;
    }

    private void write(byte[] bytes) {
        buffer.write(bytes, bytes.length);
    }
}
