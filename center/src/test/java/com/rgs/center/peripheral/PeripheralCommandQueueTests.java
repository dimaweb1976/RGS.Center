package com.rgs.center.peripheral;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeripheralCommandQueueTests {

    @Test
    void emergencyCommandIsTakenBeforeNormalCommands() throws Exception {
        PeripheralCommandQueue queue = new PeripheralCommandQueue(3);
        queue.submit(bytes("normal-1"));
        queue.submit(bytes("normal-2"));
        queue.submitFirst(bytes("emergency"));

        assertArrayEquals(bytes("emergency"), queue.take());
        assertArrayEquals(bytes("normal-1"), queue.take());
        assertArrayEquals(bytes("normal-2"), queue.take());
    }

    @Test
    void rejectsCommandWhenBoundedQueueIsFull() {
        PeripheralCommandQueue queue = new PeripheralCommandQueue(1);
        queue.submit(bytes("first"));

        assertThrows(
                RejectedExecutionException.class,
                () -> queue.submit(bytes("second"))
        );
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
