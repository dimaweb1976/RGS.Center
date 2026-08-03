package com.rgs.center.peripheral;

/** Выделяет двоичные кадры по уникальным начальному и конечному байтам. */
public final class DelimitedFrameDecoder implements PeripheralFrameDecoder {

    private final byte startByte;
    private final byte endByte;
    private final int maxFrameLength;

    /**
     * @param startByte признак начала кадра
     * @param endByte признак окончания кадра
     * @param maxFrameLength максимальная длина кадра вместе с маркерами
     */
    public DelimitedFrameDecoder(
            byte startByte,
            byte endByte,
            int maxFrameLength
    ) {
        if (startByte == endByte) {
            throw new IllegalArgumentException(
                    "Start and end bytes must be different"
            );
        }
        if (maxFrameLength < 2) {
            throw new IllegalArgumentException(
                    "Maximum frame length must be at least 2"
            );
        }
        this.startByte = startByte;
        this.endByte = endByte;
        this.maxFrameLength = maxFrameLength;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] pollFrame(ByteRingBuffer buffer) {
        return buffer.pollDelimited(startByte, endByte, maxFrameLength);
    }
}
