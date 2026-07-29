package com.rgs.center.peripheral;

/**
 * Выделяет один законченный транспортный кадр из входного кольцевого буфера.
 */
@FunctionalInterface
public interface PeripheralFrameDecoder {

    /**
     * Пытается извлечь один кадр, не блокируя вызывающий поток.
     *
     * @param buffer кольцевой буфер входящих байтов
     * @return готовый кадр либо {@code null}, если данных пока недостаточно
     */
    byte[] pollFrame(ByteRingBuffer buffer);
}
