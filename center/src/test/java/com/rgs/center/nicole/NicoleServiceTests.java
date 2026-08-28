package com.rgs.center.nicole;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import com.rgs.center.service.CurrentDataService;
import com.rgs.center.service.DeviceViewService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NicoleServiceTests {

    @Test
    void separatesSuccessfulConnectionFromTechnicalAlert() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CompletableFuture<Void> server = CompletableFuture.runAsync(
                    () -> serveOnePong(serverSocket)
            );
            TestContext context = context(serverSocket.getLocalPort());

            try {
                context.service.poll();

                assertEquals(DeviceStatus.ONLINE, context.service.getNicole().getStatus());
                assertEquals(
                        NicoleConnectionStatus.ONLINE,
                        context.service.statusView().connectionStatus()
                );
                assertEquals(
                        NicoleReadinessStatus.ALERT,
                        context.service.statusView().readiness()
                );
                assertNotNull(context.service.statusView().snapshot());
                assertEquals(
                        111,
                        context.service.statusView().snapshot().currentNitrogenLevel()
                );
                assertEquals(
                        "No pressure",
                        context.service.statusView().warningText()
                );
            } finally {
                context.service.stopCommunication();
            }
            server.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void marksAlertWhenTcpEndpointDoesNotAnswer() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        TestContext context = context(unusedPort);

        context.service.poll();

        assertEquals(DeviceStatus.OFFLINE, context.service.getNicole().getStatus());
        assertEquals(
                NicoleConnectionStatus.OFFLINE,
                context.service.statusView().connectionStatus()
        );
        assertEquals(
                NicoleReadinessStatus.NO_DATA,
                context.service.statusView().readiness()
        );
    }

    @Test
    void marksAlertWhenTcpEndpointReturnsInvalidPong() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            byte[] invalidPong = NicoleProtocolTests.pongFrame();
            invalidPong[2] ^= 0x01;
            CompletableFuture<Void> server = CompletableFuture.runAsync(
                    () -> serveResponse(serverSocket, invalidPong)
            );
            TestContext context = context(serverSocket.getLocalPort());

            try {
                context.service.poll();

                assertEquals(DeviceStatus.OFFLINE, context.service.getNicole().getStatus());
                assertEquals(
                        NicoleConnectionStatus.OFFLINE,
                        context.service.statusView().connectionStatus()
                );
                assertEquals(
                        NicoleReadinessStatus.NO_DATA,
                        context.service.statusView().readiness()
                );
            } finally {
                context.service.stopCommunication();
            }
            server.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void publishesOnlyOneReadyToAlertTransition() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CompletableFuture<Void> server = CompletableFuture.runAsync(
                    () -> serveResponses(
                            serverSocket,
                            NicoleProtocolTests.healthyPongFrame(),
                            NicoleProtocolTests.pongFrame(),
                            NicoleProtocolTests.pongFrame()
                    )
            );
            List<Object> events = new CopyOnWriteArrayList<>();
            TestContext context = context(
                    serverSocket.getLocalPort(),
                    events::add
            );

            try {
                context.service.poll();
                context.service.poll();
                context.service.poll();
            } finally {
                context.service.stopCommunication();
            }
            server.get(3, TimeUnit.SECONDS);

            List<NicoleTechnicalStatusChangedEvent> statusEvents = events.stream()
                    .filter(NicoleTechnicalStatusChangedEvent.class::isInstance)
                    .map(NicoleTechnicalStatusChangedEvent.class::cast)
                    .toList();
            assertEquals(2, statusEvents.size());
            assertEquals(1, statusEvents.stream()
                    .filter(NicoleTechnicalStatusChangedEvent::isReadyToAlert)
                    .count());
        }
    }

    private static TestContext context(int port) {
        return context(port, _event -> { });
    }

    private static TestContext context(
            int port,
            ApplicationEventPublisher eventPublisher
    ) {
        SettingsService settings = new SettingsService();
        ReflectionTestUtils.invokeMethod(settings, "initializeDefaults");
        settings.setValue(SettingsService.NICOLE_IP, "127.0.0.1");
        settings.setValue(SettingsService.NICOLE_PORT, (long) port);
        settings.setValue(SettingsService.NICOLE_CONNECT_TIMEOUT_MILLIS, 500L);
        settings.setValue(SettingsService.NICOLE_READ_TIMEOUT_MILLIS, 50L);
        settings.setValue(SettingsService.NICOLE_RESPONSE_TIMEOUT_MILLIS, 1000L);
        CurrentDataService currentData = new CurrentDataService();
        ReflectionTestUtils.invokeMethod(currentData, "initializeDevices");
        DeviceViewService deviceView = new DeviceViewService(settings, currentData);
        return new TestContext(
                new NicoleService(
                        settings,
                        currentData,
                        deviceView,
                        new NicoleTechnicalStatusEvaluator(),
                        eventPublisher
                ),
                currentData
        );
    }

    private static void serveOnePong(ServerSocket serverSocket) {
        serveResponse(serverSocket, NicoleProtocolTests.pongFrame());
    }

    private static void serveResponse(
            ServerSocket serverSocket,
            byte[] response
    ) {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(Math.toIntExact(Duration.ofSeconds(2).toMillis()));
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            int value;
            while ((value = socket.getInputStream().read()) >= 0) {
                request.write(value);
                if (value == Byte.toUnsignedInt(NicoleProtocol.STOP)) {
                    break;
                }
            }
            assertArrayEquals(NicoleProtocol.pingFrame(), request.toByteArray());
            socket.getOutputStream().write(response);
            socket.getOutputStream().flush();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void serveResponses(
            ServerSocket serverSocket,
            byte[]... responses
    ) {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(Math.toIntExact(Duration.ofSeconds(2).toMillis()));
            for (byte[] response : responses) {
                ByteArrayOutputStream request = new ByteArrayOutputStream();
                int value;
                while ((value = socket.getInputStream().read()) >= 0) {
                    request.write(value);
                    if (value == Byte.toUnsignedInt(NicoleProtocol.STOP)) {
                        break;
                    }
                }
                assertArrayEquals(NicoleProtocol.pingFrame(), request.toByteArray());
                socket.getOutputStream().write(response);
                socket.getOutputStream().flush();
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private record TestContext(
            NicoleService service,
            CurrentDataService currentData
    ) {
    }
}
