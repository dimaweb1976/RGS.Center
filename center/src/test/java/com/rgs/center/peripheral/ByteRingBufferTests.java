package com.rgs.center.peripheral;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteRingBufferTests {

    @Test
    void assemblesLinesFromFragmentedInput() {
        ByteRingBuffer buffer = new ByteRingBuffer(32);

        write(buffer, "ABC");
        assertNull(buffer.pollLine(16));

        write(buffer, "-123\r\nNEXT\n");

        assertArrayEquals(bytes("ABC-123"), buffer.pollLine(16));
        assertArrayEquals(bytes("NEXT"), buffer.pollLine(16));
        assertNull(buffer.pollLine(16));
        assertEquals(0, buffer.size());
    }

    @Test
    void readsFrameAfterBufferWrapAround() {
        ByteRingBuffer buffer = new ByteRingBuffer(8);

        write(buffer, "A\n");
        assertArrayEquals(bytes("A"), buffer.pollLine(6));

        write(buffer, "123");
        write(buffer, "4\r");

        assertArrayEquals(bytes("1234"), buffer.pollLine(6));
        assertEquals(0, buffer.size());
    }

    @Test
    void rejectsOverflowWithoutOverwritingUnreadBytes() {
        ByteRingBuffer buffer = new ByteRingBuffer(4);
        write(buffer, "1234");

        assertThrows(
                PeripheralCommunicationException.class,
                () -> write(buffer, "5")
        );
        assertEquals(4, buffer.size());
    }

    @Test
    void rejectsFrameLongerThanConfiguredLimit() {
        ByteRingBuffer buffer = new ByteRingBuffer(16);
        write(buffer, "123456");

        assertThrows(
                PeripheralCommunicationException.class,
                () -> buffer.pollLine(5)
        );
    }

    @Test
    void extractsFixedLengthBinaryFrame() {
        ByteRingBuffer buffer = new ByteRingBuffer(8);
        byte[] value = new byte[]{1, 2, 3, 4};
        buffer.write(value, value.length);

        assertArrayEquals(new byte[]{1, 2, 3}, buffer.pollBytes(3));
        assertEquals(1, buffer.size());
        assertNull(buffer.pollBytes(2));
    }

    private void write(ByteRingBuffer buffer, String value) {
        byte[] bytes = bytes(value);
        buffer.write(bytes, bytes.length);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
