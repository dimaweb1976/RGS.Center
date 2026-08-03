package com.rgs.center.peripheral;

/** Выделяет кадры, заканчивающиеся заданным однобайтовым терминатором. */
public final class TerminatedFrameDecoder implements PeripheralFrameDecoder {

    private final byte terminator;
    private final int maxFrameLength;

    /**
     * @param terminator последний байт полного кадра
     * @param maxFrameLength максимальная длина вместе с терминатором
     */
    public TerminatedFrameDecoder(byte terminator, int maxFrameLength) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException(
                    "Maximum frame length must be positive"
            );
        }
        this.terminator = terminator;
        this.maxFrameLength = maxFrameLength;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] pollFrame(ByteRingBuffer buffer) {
        return buffer.pollTerminated(terminator, maxFrameLength);
    }
}
