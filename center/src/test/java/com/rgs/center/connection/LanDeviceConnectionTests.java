package com.rgs.center.connection;

import com.rgs.center.peripheral.PeripheralDeviceWorker;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LanDeviceConnectionTests {

    @Test
    void pingUsesCommonTransmitAndReceiveQueues() {
        AtomicInteger probes = new AtomicInteger();
        LanDeviceConnection connection = new LanDeviceConnection(
                "lan:test",
                () -> "192.168.1.20",
                () -> 100,
                (address, timeout) -> {
                    probes.incrementAndGet();
                    return true;
                }
        );
        PeripheralDeviceWorker worker = new PeripheralDeviceWorker(
                "test-lan-device",
                connection,
                PeripheralWorkerConfiguration.lineDevice(32)
        );

        try (worker) {
            worker.start();
            byte[] response = worker.exchange(
                    LanDeviceConnection.PING_COMMAND,
                    Duration.ofSeconds(1)
            );

            PeripheralWorkerSnapshot snapshot = worker.snapshot();
            assertTrue(LanDeviceConnection.isOnlineResponse(response));
            assertTrue(snapshot.bytesSent() > 0);
            assertTrue(snapshot.bytesReceived() > 0);
            assertTrue(snapshot.framesReceived() > 0);
            assertTrue(probes.get() > 0);
        }
    }
}
