package com.rgs.center.shp;

import com.rgs.center.peripheral.ByteRingBuffer;
import com.rgs.center.peripheral.DelimitedFrameDecoder;
import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShpProtocolTests {

    @Test
    void crcMatchesProtocolExamples() {
        assertEquals(
                0x83,
                ShpProtocol.crc8(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}) & 0xFF
        );
        assertEquals(
                0x05,
                ShpProtocol.crc8(new byte[]{0x71, 0x05, 0, 0, 0, 0, 0})
                        & 0xFF
        );
    }

    @Test
    void createsKnownDetectorCurrentCommand() {
        assertArrayEquals(
                new byte[]{
                        (byte) 0xC0,
                        0x71, 0x05, 0, 0, 0, 0, 0,
                        0x05,
                        0x0C
                },
                ShpProtocol.readSensorCommand(5)
        );
    }

    @Test
    void parsesBigEndianSensorFloatAndByteStuffing() {
        byte[] body = new byte[17];
        body[0] = (byte) 0x91;
        body[1] = 0x04;
        byte[] value = ByteBuffer.allocate(Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putFloat(-12.25f)
                .array();
        System.arraycopy(value, 0, body, 2, value.length);
        body[8] = (byte) 0xC0;
        body[9] = (byte) 0x0C;
        body[10] = (byte) 0xAA;

        byte[] frame = ShpProtocol.encodeAnswer(body);

        assertEquals(-12.25, ShpProtocol.parseSensorValue(frame, 4));
        assertArrayEquals(body, ShpProtocol.decode(frame));
    }

    @Test
    void parsesFeedbackResistance() {
        byte[] body = new byte[17];
        body[0] = (byte) 0x95;
        body[1] = 0x07;
        body[2] = 0;
        body[3] = 10;

        assertEquals(
                10,
                ShpProtocol.parseFeedbackResistance(
                        ShpProtocol.encodeAnswer(body)
                )
        );
    }

    @Test
    void rejectsCorruptedCrc() {
        byte[] body = new byte[17];
        body[0] = (byte) 0x91;
        body[1] = 0x03;
        byte[] frame = ShpProtocol.encodeAnswer(body);
        frame[frame.length - 2] ^= 0x01;

        assertThrows(
                PeripheralCommunicationException.class,
                () -> ShpProtocol.parseSensorValue(frame, 3)
        );
    }

    @Test
    void delimiterDecoderDropsNoiseAndWaitsForStop() {
        ByteRingBuffer buffer = new ByteRingBuffer(64);
        DelimitedFrameDecoder decoder = new DelimitedFrameDecoder(
                ShpProtocol.START,
                ShpProtocol.STOP,
                32
        );
        buffer.write(new byte[]{0x01, 0x02, (byte) 0xC0, 0x71}, 4);
        assertNull(decoder.pollFrame(buffer));

        buffer.write(new byte[]{0x05, 0x0C}, 2);

        assertArrayEquals(
                new byte[]{(byte) 0xC0, 0x71, 0x05, 0x0C},
                decoder.pollFrame(buffer)
        );
    }
}
