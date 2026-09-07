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

    /**
     * Пытается завершить кадр после того, как транспорт сообщил паузу во
     * входном потоке.
     *
     * <p>По умолчанию пауза не является границей кадра. Декодеры устройств,
     * которые не передают терминатор, могут переопределить этот метод.</p>
     *
     * @param buffer кольцевой буфер входящих байтов
     * @return готовый кадр либо {@code null}
     */
    default byte[] pollIdleFrame(ByteRingBuffer buffer) {
        return null;
    }
}
