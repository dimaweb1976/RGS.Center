package com.rgs.center.scales;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralTransport;
import com.rgs.center.service.CurrentDataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScalesServiceTests {

    @Test
    void pollPublishesConnectedZeroReading() {
        FakeTransport transport = new FakeTransport();
        transport.respondWith(
                withCrLf(ScalesProtocolTests.frame(' ', ' ', "0.000", "g"))
        );
        TestContext context = context(transport);

        try {
            context.service().poll();

            ScalesSnapshot snapshot = context.service().snapshot();
            assertTrue(snapshot.connected());
            assertNotNull(snapshot.reading());
            assertTrue(snapshot.reading().isZeroGrams());
            assertEquals("COM7", snapshot.portName());
            assertEquals(9600, snapshot.baudRate());
            assertArrayEquals(
                    ScalesProtocol.immediateMassCommand(),
                    transport.lastWritten()
            );
            assertTrue(context.currentData().getValue(
                    DeviceCode.SCALES,
                    CurrentDataService.PINGED,
                    Boolean.class
            ));
        } finally {
            context.service().stopCommunication();
        }
    }

    @Test
    void malformedReplyMarksCommunicationOfflineAndRetainsLastWeight() {
        FakeTransport transport = new FakeTransport();
        transport.respondWith(
                withCrLf(ScalesProtocolTests.frame(' ', ' ', "12.5", "g"))
        );
        TestContext context = context(transport);

        try {
            context.service().poll();
            transport.respondWith("SI broken\r\n".getBytes(StandardCharsets.US_ASCII));
            context.service().poll();

            ScalesSnapshot snapshot = context.service().snapshot();
            assertFalse(snapshot.connected());
            assertEquals(new java.math.BigDecimal("12.5"), snapshot.reading().mass());
            assertFalse(context.currentData().getValue(
                    DeviceCode.SCALES,
                    CurrentDataService.PINGED,
                    Boolean.class
            ));
            assertFalse(snapshot.lastError().isBlank());
        } finally {
            context.service().stopCommunication();
        }
    }

    private static TestContext context(FakeTransport transport) {
        CurrentDataService currentData = new CurrentDataService();
        ReflectionTestUtils.invokeMethod(currentData, "initializeDevices");
        return new TestContext(
                new ScalesService(
                        currentData,
                        transport,
                        "COM7",
                        9600,
                        Duration.ofMillis(500)
                ),
                currentData
        );
    }

    private static byte[] withCrLf(byte[] frame) {
        byte[] result = Arrays.copyOf(frame, frame.length + 2);
        result[result.length - 2] = '\r';
        result[result.length - 1] = '\n';
        return result;
    }

    private record TestContext(
            ScalesService service,
            CurrentDataService currentData
    ) {
    }

    private static final class FakeTransport implements PeripheralTransport {
        private final AtomicBoolean open = new AtomicBoolean();
        private final BlockingQueue<byte[]> incoming = new LinkedBlockingQueue<>();
        private volatile byte[] responseAfterWrite;
        private volatile byte[] lastWritten = new byte[0];

        @Override
        public void open() {
            open.set(true);
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public int read(byte[] buffer) {
            if (!isOpen()) {
                return -1;
            }
            try {
                byte[] value = incoming.poll(50, TimeUnit.MILLISECONDS);
                if (value == null) {
                    return 0;
                }
                System.arraycopy(value, 0, buffer, 0, value.length);
                return value.length;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }

        @Override
        public void write(byte[] value) {
            lastWritten = Arrays.copyOf(value, value.length);
            byte[] response = responseAfterWrite;
            if (response != null) {
                incoming.add(Arrays.copyOf(response, response.length));
            }
        }

        @Override
        public void clearInput() {
            incoming.clear();
        }

        @Override
        public void close() {
            open.set(false);
        }

        void respondWith(byte[] response) {
            responseAfterWrite = Arrays.copyOf(response, response.length);
        }

        byte[] lastWritten() {
            return Arrays.copyOf(lastWritten, lastWritten.length);
        }
    }
}
