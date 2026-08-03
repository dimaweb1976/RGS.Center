package com.rgs.center.peripheral;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeripheralDeviceWorkerTests {

    @Test
    void receivesFragmentedFrameAndPublishesStatistics() {
        FakeTransport transport = new FakeTransport();
        PeripheralDeviceWorker worker = worker(transport);

        try (worker) {
            worker.start();
            transport.emit("CODE-");
            transport.emit("123\r\n");

            byte[] frame = worker.awaitFrame(Duration.ofSeconds(1))
                    .orElseThrow();
            assertArrayEquals(bytes("CODE-123"), frame);

            PeripheralWorkerSnapshot snapshot = worker.snapshot();
            assertEquals(PeripheralWorkerState.RUNNING, snapshot.state());
            assertEquals(10, snapshot.bytesReceived());
            assertEquals(1, snapshot.framesReceived());
            assertTrue(snapshot.lastReceiveEpochMillis() > 0);
        }

        assertFalse(transport.isOpen());
        assertEquals(PeripheralWorkerState.STOPPED, worker.snapshot().state());
    }

    @Test
    void transmitsQueuedCommandOnDedicatedWorker() {
        FakeTransport transport = new FakeTransport();
        PeripheralDeviceWorker worker = worker(transport);

        try (worker) {
            worker.start();
            worker.submit(bytes("GET\r\n"));

            await().atMost(1, TimeUnit.SECONDS)
                    .until(() -> transport.written().size() == 1);

            assertArrayEquals(
                    bytes("GET\r\n"),
                    transport.written().getFirst()
            );
            assertEquals(5, worker.snapshot().bytesSent());
            assertTrue(worker.snapshot().lastTransmitEpochMillis() > 0);
        }
    }

    @Test
    void clearsStaleTransportInputBeforeStartingThreads() {
        FakeTransport transport = new FakeTransport();
        PeripheralDeviceWorker worker = worker(transport);

        try (worker) {
            worker.start();
            assertTrue(transport.wasInputCleared());
        }
    }

    @Test
    void performsSequentialRequestResponseExchange() {
        FakeTransport transport = new FakeTransport();
        transport.respondWith("OK\r\n");
        PeripheralDeviceWorker worker = worker(transport);

        try (worker) {
            worker.start();

            byte[] response = worker.exchange(
                    bytes("STATUS\r\n"),
                    Duration.ofSeconds(1)
            );

            assertArrayEquals(bytes("OK"), response);
            assertArrayEquals(
                    bytes("STATUS\r\n"),
                    transport.written().getFirst()
            );
        }
    }

    private PeripheralDeviceWorker worker(FakeTransport transport) {
        return new PeripheralDeviceWorker(
                "test-device",
                transport,
                new PeripheralWorkerConfiguration(
                        64,
                        8,
                        8,
                        8,
                        32,
                        true
                )
        );
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeTransport implements PeripheralTransport {

        private final AtomicBoolean open = new AtomicBoolean();
        private final AtomicBoolean inputCleared = new AtomicBoolean();
        private final BlockingQueue<byte[]> incoming =
                new LinkedBlockingQueue<>();
        private final List<byte[]> written = new CopyOnWriteArrayList<>();
        private volatile byte[] responseAfterWrite;

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
            written.add(Arrays.copyOf(value, value.length));
            if (responseAfterWrite != null) {
                incoming.add(Arrays.copyOf(
                        responseAfterWrite,
                        responseAfterWrite.length
                ));
            }
        }

        @Override
        public void clearInput() {
            incoming.clear();
            inputCleared.set(true);
        }

        @Override
        public void close() {
            open.set(false);
        }

        void emit(String value) {
            incoming.add(value.getBytes(StandardCharsets.UTF_8));
        }

        List<byte[]> written() {
            return written;
        }

        boolean wasInputCleared() {
            return inputCleared.get();
        }

        void respondWith(String value) {
            responseAfterWrite = value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
