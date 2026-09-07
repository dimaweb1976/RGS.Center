package com.rgs.center.barcodescanner;

import com.rgs.center.peripheral.PeripheralTransport;
import com.rgs.center.service.CurrentDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JSerialCommBarcodeScannerPortTests {

    private CurrentDataService currentData;

    @BeforeEach
    void setUp() {
        currentData = new CurrentDataService();
        ReflectionTestUtils.invokeMethod(currentData, "initializeDevices");
    }

    @Test
    void initializesReadsBarcodeAndAlwaysTurnsTriggerAndIlluminationOff() {
        FakeScannerTransport transport = new FakeScannerTransport("ABC-123");
        JSerialCommBarcodeScannerPort port = port(transport, 2);

        try {
            assertEquals(
                    "ABC-123",
                    port.readCode("COM8", 9600, 200, 64)
            );

            List<byte[]> written = transport.written();
            assertEquals(1, count(written, BarcodeScannerProtocol.activateTrigger()));
            assertEquals(2, count(written, BarcodeScannerProtocol.deactivateTrigger()));
            assertEquals(1, count(written, BarcodeScannerProtocol.illuminationOn()));
            assertEquals(2, count(written, BarcodeScannerProtocol.illuminationOff()));
            assertEquals(
                    BarcodeScannerOperationState.SUCCESS,
                    port.snapshot().operationState()
            );
            assertTrue(port.snapshot().connected());
        } finally {
            port.stopCommunication();
        }
    }

    @Test
    void retriesCompleteCycleAndKeepsConnectionOnlineAfterNoRead() {
        FakeScannerTransport transport = new FakeScannerTransport(null);
        JSerialCommBarcodeScannerPort port = port(transport, 2);

        try {
            assertThrows(
                    BarcodeScannerNoReadException.class,
                    () -> port.readCode("COM8", 9600, 25, 64)
            );

            List<byte[]> written = transport.written();
            assertEquals(2, count(written, BarcodeScannerProtocol.activateTrigger()));
            assertEquals(3, count(written, BarcodeScannerProtocol.deactivateTrigger()));
            assertEquals(2, count(written, BarcodeScannerProtocol.illuminationOn()));
            assertEquals(3, count(written, BarcodeScannerProtocol.illuminationOff()));
            assertEquals(
                    BarcodeScannerOperationState.TIMEOUT,
                    port.snapshot().operationState()
            );
            assertTrue(port.snapshot().connected());
        } finally {
            port.stopCommunication();
        }
    }

    @Test
    void readsBarcodeWithConfiguredCrLfAndStripsTheSuffix() {
        FakeScannerTransport transport = new FakeScannerTransport(
                "CRLF-CODE\r\n"
        );
        JSerialCommBarcodeScannerPort port = port(
                transport,
                1,
                BarcodeScannerDataSuffix.CRLF
        );

        try {
            assertEquals(
                    "CRLF-CODE",
                    port.readCode("COM8", 9600, 200, 64)
            );
        } finally {
            port.stopCommunication();
        }
    }

    private JSerialCommBarcodeScannerPort port(
            PeripheralTransport transport,
            int attempts
    ) {
        return port(transport, attempts, BarcodeScannerDataSuffix.AUTO);
    }

    private JSerialCommBarcodeScannerPort port(
            PeripheralTransport transport,
            int attempts,
            BarcodeScannerDataSuffix dataSuffix
    ) {
        return new JSerialCommBarcodeScannerPort(
                currentData,
                transport,
                "COM8",
                9600,
                64,
                Duration.ofMillis(200),
                attempts,
                Duration.ZERO,
                dataSuffix
        );
    }

    private long count(List<byte[]> values, byte[] expected) {
        return values.stream()
                .filter(value -> Arrays.equals(value, expected))
                .count();
    }

    private static final class FakeScannerTransport
            implements PeripheralTransport {

        private final AtomicBoolean open = new AtomicBoolean();
        private final BlockingQueue<byte[]> incoming =
                new LinkedBlockingQueue<>();
        private final List<byte[]> written = new CopyOnWriteArrayList<>();
        private final String barcode;

        private FakeScannerTransport(String barcode) {
            this.barcode = barcode;
        }

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
            if (!open.get()) {
                return -1;
            }
            try {
                byte[] value = incoming.poll(20, TimeUnit.MILLISECONDS);
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
            if (isMenuCommand(value)) {
                respondToMenu(value);
            } else if (Arrays.equals(
                    value,
                    BarcodeScannerProtocol.activateTrigger()
            ) && barcode != null) {
                incoming.add(barcode.getBytes(StandardCharsets.US_ASCII));
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

        private boolean isMenuCommand(byte[] value) {
            return value.length >= 5
                    && value[0] == 0x16
                    && value[1] == 'M'
                    && value[2] == 0x0D;
        }

        private void respondToMenu(byte[] command) {
            String body = new String(
                    command,
                    3,
                    command.length - 4,
                    StandardCharsets.US_ASCII
            );
            String echoed = body.endsWith("?")
                    ? body.substring(0, body.length() - 1) + "0"
                    : body;
            byte[] text = echoed.getBytes(StandardCharsets.US_ASCII);
            byte[] response = Arrays.copyOf(text, text.length + 2);
            response[text.length] = BarcodeScannerProtocol.ACK;
            response[text.length + 1] = '!';
            incoming.add(response);
        }

        private List<byte[]> written() {
            return written;
        }
    }
}
