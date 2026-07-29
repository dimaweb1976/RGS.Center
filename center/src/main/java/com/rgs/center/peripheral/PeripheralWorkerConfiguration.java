package com.rgs.center.peripheral;

/**
 * Ограничения очередей и буферов одного периферийного worker.
 *
 * @param receiveBufferCapacity размер кольцевого RX-буфера
 * @param commandQueueCapacity максимальное число ожидающих команд
 * @param frameQueueCapacity максимальное число готовых кадров
 * @param readChunkSize размер одной порции чтения транспорта
 * @param maxFrameLength максимально допустимая длина кадра
 * @param discardInputOnStart очищать ли старые данные при запуске
 */
public record PeripheralWorkerConfiguration(
        int receiveBufferCapacity,
        int commandQueueCapacity,
        int frameQueueCapacity,
        int readChunkSize,
        int maxFrameLength,
        boolean discardInputOnStart
) {

    /** Проверяет согласованность ограничений при создании конфигурации. */
    public PeripheralWorkerConfiguration {
        requirePositive(receiveBufferCapacity, "Receive buffer capacity");
        requirePositive(commandQueueCapacity, "Command queue capacity");
        requirePositive(frameQueueCapacity, "Frame queue capacity");
        requirePositive(readChunkSize, "Read chunk size");
        requirePositive(maxFrameLength, "Maximum frame length");
        if (receiveBufferCapacity <= maxFrameLength) {
            throw new IllegalArgumentException(
                    "Receive buffer must be larger than maximum frame length"
            );
        }
    }

    /**
     * Создаёт рекомендуемую конфигурацию для строкового COM-протокола.
     *
     * @param maxFrameLength максимальная длина строки
     * @return конфигурация с ограниченными очередями и запасом RX-буфера
     */
    public static PeripheralWorkerConfiguration lineDevice(
            int maxFrameLength
    ) {
        int receiveCapacity = Math.max(8192, maxFrameLength + 1024);
        return new PeripheralWorkerConfiguration(
                receiveCapacity,
                64,
                64,
                256,
                maxFrameLength,
                true
        );
    }

    /**
     * Проверяет, что размер очереди или буфера положительный.
     *
     * @param value проверяемое значение
     * @param name название параметра для сообщения об ошибке
     */
    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
