package com.rgs.center.manipulator;

import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DobotFeedbackParserTests {

    private final DobotFeedbackParser parser = new DobotFeedbackParser();

    @Test
    void parsesDocumentedFieldsFromLittleEndianPacket() {
        byte[] frame = validFrame();
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(8, 0b11L);
        buffer.putLong(16, 7L);
        buffer.putLong(24, 5L);
        buffer.putLong(32, 123456L);
        for (int index = 0; index < 6; index++) {
            buffer.putDouble(432 + index * 8, index + 0.5);
            buffer.putDouble(624 + index * 8, 100.0 + index);
        }
        buffer.put(1014, (byte) 1);
        buffer.put(1026, (byte) 1);
        buffer.put(1028, (byte) 0);
        buffer.put(1029, (byte) 0);
        buffer.putLong(1112, 42L);
        buffer.put(1420, (byte) 3);

        DobotFeedbackFrame result = parser.parse(frame);

        assertEquals(3L, result.digitalInputs());
        assertEquals(5L, result.robotMode());
        assertEquals(42L, result.currentCommandId());
        assertEquals(100.0, result.pose().x());
        assertEquals(105.0, result.pose().rz());
        assertEquals(6, result.actualJointPositions().size());
        assertTrue(result.queuedCommandRunning());
        assertTrue(result.enabled());
    }

    @Test
    void rejectsWrongLengthAndTestMarker() {
        assertThrows(
                PeripheralCommunicationException.class,
                () -> parser.parse(new byte[10])
        );
        byte[] frame = validFrame();
        ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).putLong(48, 0L);
        assertThrows(
                PeripheralCommunicationException.class,
                () -> parser.parse(frame)
        );
    }

    private static byte[] validFrame() {
        byte[] frame = new byte[DobotFeedbackParser.FRAME_LENGTH];
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) DobotFeedbackParser.FRAME_LENGTH);
        buffer.putLong(48, DobotFeedbackParser.TEST_VALUE);
        return frame;
    }
}
