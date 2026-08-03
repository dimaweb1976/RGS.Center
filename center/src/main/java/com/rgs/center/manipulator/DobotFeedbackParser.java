package com.rgs.center.manipulator;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Parses the documented little-endian Dobot real-time feedback packet. */
public final class DobotFeedbackParser {

    public static final int FRAME_LENGTH = 1440;
    public static final long TEST_VALUE = 0x0123456789ABCDEFL;

    /**
     * Parses fields used by RGS.Center and validates both packet size markers.
     *
     * @param frame exactly one 1440-byte feedback packet
     * @return decoded, immutable telemetry
     */
    public DobotFeedbackFrame parse(byte[] frame) {
        if (frame == null || frame.length != FRAME_LENGTH) {
            throw new PeripheralCommunicationException(
                    "Dobot feedback frame must contain " + FRAME_LENGTH + " bytes"
            );
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int declaredSize = Short.toUnsignedInt(buffer.getShort(0));
        if (declaredSize != FRAME_LENGTH) {
            throw new PeripheralCommunicationException(
                    "Unexpected Dobot feedback size: " + declaredSize
            );
        }
        if (buffer.getLong(48) != TEST_VALUE) {
            throw new PeripheralCommunicationException(
                    "Invalid Dobot feedback test value"
            );
        }

        List<Double> joints = doubles(buffer, 432, 6);
        List<Double> poseValues = doubles(buffer, 624, 6);
        CartesianPose pose = new CartesianPose(
                poseValues.get(0), poseValues.get(1), poseValues.get(2),
                poseValues.get(3), poseValues.get(4), poseValues.get(5)
        );
        return new DobotFeedbackFrame(
                buffer.getLong(8),
                buffer.getLong(16),
                buffer.getLong(24),
                buffer.getLong(32),
                joints,
                pose,
                buffer.get(1014) != 0,
                buffer.get(1026) != 0,
                buffer.get(1028) != 0,
                buffer.get(1029) != 0,
                buffer.getLong(1112),
                Byte.toUnsignedInt(buffer.get(1420))
        );
    }

    /** Reads a fixed number of consecutive IEEE-754 doubles. */
    private static List<Double> doubles(
            ByteBuffer buffer,
            int offset,
            int count
    ) {
        List<Double> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(buffer.getDouble(offset + index * Double.BYTES));
        }
        return values;
    }
}
