package com.rgs.center.peripheral;

/**
 * Выделяет бинарные кадры заранее известной фиксированной длины.
 */
public final class FixedLengthFrameDecoder
        implements PeripheralFrameDecoder {

    /** Размер одного бинарного кадра в байтах. */
    private final int frameLength;

    /**
     * @param frameLength точная длина одного кадра
     */
    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException(
                    "Frame length must be positive"
            );
        }
        this.frameLength = frameLength;
    }

    /**
     * Извлекает кадр, когда в буфере накопилось достаточно байтов.
     *
     * @param buffer входной кольцевой буфер
     * @return кадр фиксированной длины или {@code null}
     */
    @Override
    public byte[] pollFrame(ByteRingBuffer buffer) {
        return buffer.pollBytes(frameLength);
    }
}
