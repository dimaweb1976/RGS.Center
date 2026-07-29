package com.rgs.center.peripheral;

/**
 * Выделяет текстовые кадры, завершённые CR, LF или последовательностью CR/LF.
 */
public final class LineFrameDecoder implements PeripheralFrameDecoder {

    /** Максимальное число байтов без символа конца строки. */
    private final int maxFrameLength;

    /**
     * @param maxFrameLength максимально допустимая длина кадра без CR/LF
     */
    public LineFrameDecoder(int maxFrameLength) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException(
                    "Maximum frame length must be positive"
            );
        }
        this.maxFrameLength = maxFrameLength;
    }

    /**
     * Извлекает ближайшую завершённую строку из кольцевого буфера.
     *
     * @param buffer входной кольцевой буфер
     * @return кадр без CR/LF или {@code null}
     */
    @Override
    public byte[] pollFrame(ByteRingBuffer buffer) {
        return buffer.pollLine(maxFrameLength);
    }
}
