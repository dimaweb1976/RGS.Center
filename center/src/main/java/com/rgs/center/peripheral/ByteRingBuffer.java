package com.rgs.center.peripheral;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Ограниченный потокобезопасный кольцевой буфер входящих байтов.
 *
 * <p>RX-поток дописывает в него порции данных, а декодер удаляет готовые
 * кадры. Все изменения указателя начала и размера выполняются под одним
 * {@link ReentrantLock}, поэтому данные не могут быть прочитаны в момент
 * частичной записи.</p>
 */
public final class ByteRingBuffer {

    /** Физический массив фиксированной ёмкости. */
    private final byte[] data;

    /** Общая блокировка массива, указателя начала и текущего размера. */
    private final ReentrantLock lock = new ReentrantLock();

    /** Индекс первого непрочитанного байта. */
    private int head;

    /** Число доступных для чтения байтов. */
    private int size;

    /**
     * @param capacity максимальное число одновременно накопленных байтов
     */
    public ByteRingBuffer(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException(
                    "Buffer capacity must be at least 2"
            );
        }
        data = new byte[capacity];
    }

    /**
     * Атомарно дописывает порцию данных, не перезаписывая непрочитанные байты.
     *
     * @param source исходный массив
     * @param length число используемых байтов в исходном массиве
     * @throws PeripheralCommunicationException если свободного места не хватает
     */
    public void write(byte[] source, int length) {
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null");
        }
        if (length < 0 || length > source.length) {
            throw new IllegalArgumentException("Invalid source length");
        }

        lock.lock();
        try {
            // Переполнение считается ошибкой обмена: терять старые байты нельзя.
            if (length > data.length - size) {
                throw new PeripheralCommunicationException(
                        "Receive buffer overflow: capacity=" + data.length
                        + ", buffered=" + size
                        + ", incoming=" + length
                );
            }

            // Хвост вычисляется относительно head и автоматически оборачивается.
            int tail = (head + size) % data.length;
            for (int index = 0; index < length; index++) {
                data[tail] = source[index];
                tail = (tail + 1) % data.length;
            }
            size += length;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Извлекает строковый кадр до CR/LF.
     *
     * @param maxLength максимальная длина полезных данных без CR/LF
     * @return кадр без символов завершения или {@code null}
     */
    public byte[] pollLine(int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException(
                    "Maximum frame length must be positive"
            );
        }

        lock.lock();
        try {
            // Пустые строки и второй байт CR/LF не передаются потребителю.
            discardLeadingLineEndings();
            for (int offset = 0; offset < size; offset++) {
                byte value = valueAt(offset);
                if (isLineEnding(value)) {
                    byte[] frame = remove(offset);
                    discardOne();
                    // CR/LF рассматривается как один разделитель кадра.
                    if (value == '\r' && size > 0 && valueAt(0) == '\n') {
                        discardOne();
                    }
                    return frame;
                }
                if (offset + 1 > maxLength) {
                    throw new PeripheralCommunicationException(
                            "Received frame exceeds maximum length of "
                            + maxLength
                    );
                }
            }
            if (size > maxLength) {
                throw new PeripheralCommunicationException(
                        "Received frame exceeds maximum length of " + maxLength
                );
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return число ещё не обработанных входящих байтов
     */
    public int size() {
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Извлекает строго заданное число байтов для бинарного протокола.
     *
     * @param length требуемый размер кадра
     * @return кадр либо {@code null}, если данных пока недостаточно
     */
    public byte[] pollBytes(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException(
                    "Requested length must be positive"
            );
        }
        lock.lock();
        try {
            return size < length ? null : remove(length);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Извлекает двоичный кадр вместе с начальным и конечным маркерами.
     * Байты до первого начального маркера считаются шумом и удаляются.
     *
     * @param startByte признак начала кадра
     * @param endByte признак окончания кадра
     * @param maxLength максимальная длина кадра вместе с маркерами
     * @return полный кадр либо {@code null}, если конечный маркер ещё не получен
     */
    public byte[] pollDelimited(
            byte startByte,
            byte endByte,
            int maxLength
    ) {
        if (maxLength < 2) {
            throw new IllegalArgumentException(
                    "Maximum delimited frame length must be at least 2"
            );
        }
        lock.lock();
        try {
            while (size > 0 && valueAt(0) != startByte) {
                discardOne();
            }
            for (int offset = 1; offset < size; offset++) {
                if (offset + 1 > maxLength) {
                    throw new PeripheralCommunicationException(
                            "Received delimited frame exceeds maximum length of "
                                    + maxLength
                    );
                }
                if (valueAt(offset) == endByte) {
                    return remove(offset + 1);
                }
            }
            if (size > maxLength) {
                throw new PeripheralCommunicationException(
                        "Received delimited frame exceeds maximum length of "
                                + maxLength
                );
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return физическая ёмкость буфера
     */
    public int capacity() {
        return data.length;
    }

    /** Удаляет все накопленные байты и возвращает указатели в начало. */
    public void clear() {
        lock.lock();
        try {
            head = 0;
            size = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Удаляет указанное число байтов. Вызывается только под {@link #lock}.
     *
     * @param length число извлекаемых байтов
     * @return извлечённая копия
     */
    private byte[] remove(int length) {
        byte[] result = new byte[length];
        for (int index = 0; index < length; index++) {
            result[index] = valueAt(0);
            discardOne();
        }
        return result;
    }

    /** Удаляет ведущие CR/LF. Вызывается только под {@link #lock}. */
    private void discardLeadingLineEndings() {
        while (size > 0 && isLineEnding(valueAt(0))) {
            discardOne();
        }
    }

    /** Сдвигает начало кольца на один байт. Вызывается только под lock. */
    private void discardOne() {
        head = (head + 1) % data.length;
        size--;
    }

    /**
     * Возвращает байт по логическому смещению с учётом оборота кольца.
     *
     * @param offset смещение от первого непрочитанного байта
     * @return найденный байт
     */
    private byte valueAt(int offset) {
        return data[(head + offset) % data.length];
    }

    /**
     * @param value проверяемый байт
     * @return является ли он CR или LF
     */
    private boolean isLineEnding(byte value) {
        return value == '\r' || value == '\n';
    }
}
