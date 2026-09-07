package com.rgs.center.service;

import com.rgs.center.gammapro.BosonHighVoltageSnapshot;
import com.rgs.center.gammapro.BosonConnectionStatus;
import com.rgs.center.gammapro.GammaProConnectionStatus;
import com.rgs.center.gammapro.GammaProHighVoltageValueType;
import com.rgs.center.gammapro.GammaProMeasurementRequest;
import com.rgs.center.gammapro.GammaProMeasurementState;
import com.rgs.center.gammapro.GammaProProtocol;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gammapro.GammaProSnapshot;
import com.rgs.center.gammapro.GammaProStatusView;
import com.rgs.center.objects.DeviceCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GammaProServiceTests {

    private static final Charset WINDOWS_1251 =
            Charset.forName("windows-1251");

    @Test
    void performsHeartbeatAndStageSevenAcquisition() throws Exception {
        List<String> receivedCommands = new CopyOnWriteArrayList<>();
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Future<?> server = serverExecutor.submit(() -> serveGammaPro(
                    serverSocket,
                    receivedCommands
            ));

            SettingsService settings = new SettingsService();
            settings.initializeDefaults();
            settings.setValue(
                    SettingsService.GAMMA_PRO_PORT,
                    (long) serverSocket.getLocalPort()
            );
            settings.setValue(
                    SettingsService.GAMMA_PRO_READ_TIMEOUT_MILLIS,
                    50L
            );
            settings.setValue(
                    SettingsService.GAMMA_PRO_RESPONSE_TIMEOUT_MILLIS,
                    1000L
            );
            CurrentDataService currentData = new CurrentDataService();
            currentData.initializeDevices();
            GammaProService service = new GammaProService(
                    settings,
                    currentData
            );

            try {
                service.startCommunication();
                assertEquals(
                        GammaProConnectionStatus.CONNECTED,
                        service.connectionStatus()
                );
                service.pollGammaPro();
                GammaProStatusView statusView = service.statusView();
                service.selectTract("BOSON1", "GCD-4020");
                BosonHighVoltageSnapshot highVoltage =
                        service.applyBosonHighVoltage(
                                "BOSON1",
                                "GCD-4020",
                                true,
                                2500,
                                GammaProHighVoltageValueType.HIGH_VOLTAGE
                        );
                BosonHighVoltageSnapshot disabledHighVoltage =
                        service.applyBosonHighVoltage(
                                "BOSON1",
                                "GCD-4020",
                                false,
                                1000,
                                GammaProHighVoltageValueType.HIGH_VOLTAGE
                        );
                GammaProSnapshot started = service.startSpectrumAcquisition(
                        new GammaProMeasurementRequest(
                                "BOSON1",
                                "GCD-4020",
                                120
                        )
                );
                GammaProSnapshot completed =
                        service.pollSpectrumAcquisition();

                assertEquals(
                        GammaProMeasurementState.RUNNING,
                        started.measurementState()
                );
                assertEquals(
                        GammaProMeasurementState.COMPLETED,
                        completed.measurementState()
                );
                assertTrue(completed.connected());
                assertEquals(
                        GammaProConnectionStatus.CONNECTED,
                        statusView.connectionStatus()
                );
                assertEquals(
                        BosonConnectionStatus.CONNECTED,
                        statusView.bosonConnectionStatus()
                );
                assertTrue(!statusView.snapshot().tracts().isEmpty());
                assertNotNull(statusView.bosonLastConfirmedAt());
                assertNotNull(statusView.lastUpdatedAt());
                assertTrue(highVoltage.commandAccepted());
                assertEquals(2500, highVoltage.requestedValue());
                assertEquals(1000, disabledHighVoltage.requestedValue());
                assertEquals(false, disabledHighVoltage.requestedEnabled());
                assertEquals(
                        disabledHighVoltage,
                        currentData.getValue(
                                DeviceCode.BOSON,
                                CurrentDataService.LAST_DATA,
                                BosonHighVoltageSnapshot.class
                        )
                );
                assertEquals(
                        true,
                        currentData.getValue(
                                DeviceCode.GAMMA_PRO,
                                CurrentDataService.PINGED,
                                Boolean.class
                        )
                );
                assertEquals(
                        List.of(
                                "GET_TRACTS_NAME()",
                                "SET_TRACT(BOSON1,GCD-4020)",
                                "SET_TRACT(BOSON1,GCD-4020)",
                                "SET_HV(1,2500,0)",
                                "SET_TRACT(BOSON1,GCD-4020)",
                                "SET_HV(0,1000,0)",
                                "SET_TRACT(BOSON1,GCD-4020)",
                                "SET_PRESET(120)",
                                "START",
                                "WAIT_MEAS_END"
                        ),
                        receivedCommands
                );
            } finally {
                service.stopCommunication();
                assertEquals(
                        GammaProConnectionStatus.DISCONNECTED,
                        service.connectionStatus()
                );
            }

            server.get(2, TimeUnit.SECONDS);
        } finally {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void reportsRejectedManualTractCommand()
            throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Future<?> server = serverExecutor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    String command = readCommand(socket.getInputStream());
                    assertEquals("SET_TRACT(BOSON,Ch1)", command);
                    respond(socket.getOutputStream(), "SET_TRACT=ER");
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            GammaProService service = serviceFor(serverSocket, 500L);
            try {
                service.startCommunication();
                assertThrows(
                        RuntimeException.class,
                        () -> service.selectTract("BOSON", "Ch1")
                );
                assertTrue(service.snapshot().lastError().contains(
                        "rejected command"
                ));
            } finally {
                service.stopCommunication();
            }
            server.get(2, TimeUnit.SECONDS);
        } finally {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void reportsManualTractTimeout() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Future<?> server = serverExecutor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    assertEquals(
                            "SET_TRACT(BOSON,Ch1)",
                            readCommand(socket.getInputStream())
                    );
                    // Удерживаем сокет открытым дольше настроенного ожидания,
                    // имитируя Gamma-PRO, которая приняла байты без ответа.
                    Thread.sleep(200);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            GammaProService service = serviceFor(serverSocket, 75L);
            try {
                service.startCommunication();
                assertThrows(
                        RuntimeException.class,
                        () -> service.selectTract("BOSON", "Ch1")
                );
                assertTrue(service.snapshot().lastError().contains(
                        "timed out"
                ));
            } finally {
                service.stopCommunication();
            }
            server.get(2, TimeUnit.SECONDS);
        } finally {
            serverExecutor.shutdownNow();
        }
    }

    /** Создаёт сервис с короткими тайм-аутами для локального TCP-стенда. */
    private static GammaProService serviceFor(
            ServerSocket serverSocket,
            long responseTimeoutMillis
    ) {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();
        settings.setValue(
                SettingsService.GAMMA_PRO_PORT,
                (long) serverSocket.getLocalPort()
        );
        settings.setValue(
                SettingsService.GAMMA_PRO_READ_TIMEOUT_MILLIS,
                25L
        );
        settings.setValue(
                SettingsService.GAMMA_PRO_RESPONSE_TIMEOUT_MILLIS,
                responseTimeoutMillis
        );
        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        return new GammaProService(settings, currentData);
    }

    private static void serveGammaPro(
            ServerSocket serverSocket,
            List<String> receivedCommands
    ) {
        try (Socket socket = serverSocket.accept()) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            String command;
            while ((command = readCommand(input)) != null) {
                receivedCommands.add(command);
                switch (GammaProProtocol.commandName(command)) {
                    case GammaProProtocol.GET_TRACTS_NAME -> {
                        respond(
                                output,
                                "GET_TRACTS_NAME> DATA "
                                        + "TRACT1(BOSON,Ch1),"
                        );
                        respond(output, "GET_TRACTS_NAME=OK");
                    }
                    case "SET_TRACT" -> respond(output, "SET_TRACT=OK");
                    case "SET_PRESET" -> respond(output, "SET_PRESET=OK");
                    case GammaProProtocol.SET_HV -> respond(
                            output,
                            "SET_HV=OK"
                    );
                    case GammaProProtocol.START -> respond(output, "START=OK");
                    case GammaProProtocol.WAIT_MEAS_END -> {
                        respond(output, "WAIT_MEAS_END=DO");
                        respond(output, "WAIT_MEAS_END=OK");
                    }
                    default -> respond(
                            output,
                            GammaProProtocol.commandName(command) + "=ER"
                    );
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String readCommand(InputStream input) throws IOException {
        StringBuilder value = new StringBuilder();
        boolean receivedCr = false;
        while (true) {
            int current = input.read();
            if (current < 0) {
                if (value.isEmpty() && !receivedCr) {
                    return null;
                }
                throw new IOException("Command ended before CR LF");
            }
            if (receivedCr) {
                if (current == '\n') {
                    return value.toString();
                }
                throw new IOException("Gamma-PRO command must end with CR LF");
            }
            if (current == '\r') {
                receivedCr = true;
                continue;
            }
            if (current == '\n') {
                throw new IOException("Gamma-PRO command must not end with LF CR");
            }
            value.append((char) current);
        }
    }

    private static void respond(OutputStream output, String value)
            throws IOException {
        output.write((value + "\n\r").getBytes(WINDOWS_1251));
        output.flush();
    }
}
