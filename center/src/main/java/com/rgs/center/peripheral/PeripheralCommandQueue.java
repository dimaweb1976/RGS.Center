package com.rgs.center.peripheral;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;

/**
 * Ограниченная потокобезопасная очередь команд периферийного устройства.
 *
 * <p>Обычные команды добавляются в конец, а аварийные — в начало. Ограничение
 * размера защищает приложение от неограниченного расхода памяти, если
 * устройство перестало принимать команды.</p>
 */
public final class PeripheralCommandQueue {

    /** Blocking deque заменяет несинхронизированный LinkedList GDRMonitor. */
    private final LinkedBlockingDeque<byte[]> queue;

    /**
     * @param capacity максимальное число ожидающих команд
     */
    public PeripheralCommandQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "Command queue capacity must be positive"
            );
        }
        queue = new LinkedBlockingDeque<>(capacity);
    }

    /**
     * Добавляет обычную команду в конец очереди.
     *
     * @param command байты команды
     */
    public void submit(byte[] command) {
        if (!queue.offerLast(copy(command))) {
            throw queueFull();
        }
    }

    /**
     * Добавляет приоритетную команду в начало очереди.
     *
     * @param command байты аварийной или сервисной команды
     */
    public void submitFirst(byte[] command) {
        if (!queue.offerFirst(copy(command))) {
            throw queueFull();
        }
    }

    /**
     * Блокирует TX-поток до появления команды.
     *
     * @return следующая команда с учётом приоритета
     * @throws InterruptedException при штатной остановке worker
     */
    public byte[] take() throws InterruptedException {
        return queue.takeFirst();
    }

    /**
     * @return текущее число ожидающих команд
     */
    public int size() {
        return queue.size();
    }

    /** Удаляет команды, оставшиеся от предыдущего сеанса соединения. */
    public void clear() {
        queue.clear();
    }

    /**
     * Проверяет команду и копирует её для изоляции от вызывающего кода.
     *
     * @param command исходный массив
     * @return независимая копия
     */
    private byte[] copy(byte[] command) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException(
                    "Peripheral command must not be empty"
            );
        }
        return Arrays.copyOf(command, command.length);
    }

    /**
     * @return ошибка отказа в постановке при заполненной bounded-очереди
     */
    private RejectedExecutionException queueFull() {
        return new RejectedExecutionException(
                "Peripheral command queue is full"
        );
    }
}
