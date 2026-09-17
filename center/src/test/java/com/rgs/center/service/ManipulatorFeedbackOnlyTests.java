package com.rgs.center.service;

import com.rgs.center.manipulator.*;
import com.rgs.center.peripheral.PeripheralWorkerState;
import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ManipulatorFeedbackOnlyTests {
    private SettingsService settings(int feedbackPort, int commandPort) {
        var settings = new SettingsService();
        settings.initializeDefaults();
        settings.setValue(SettingsService.MANIPULATOR_HOST, "127.0.0.1");
        settings.setValue(SettingsService.MANIPULATOR_FEEDBACK_PORT, (long) feedbackPort);
        settings.setValue(SettingsService.MANIPULATOR_COMMAND_PORT, (long) commandPort);
        settings.setValue(SettingsService.MANIPULATOR_READ_TIMEOUT_MILLIS, 20L);
        settings.setValue(SettingsService.MANIPULATOR_FEEDBACK_ONLY, true);
        settings.setValue(SettingsService.MANIPULATOR_HATCH_AI_ENABLED, true);
        return settings;
    }

    private RoboticManipulatorStreamService service(SettingsService settings) {
        var data = new CurrentDataService();
        data.initializeDevices();
        return new RoboticManipulatorStreamService(settings, data);
    }

    private byte[] frame() {
        var buffer = ByteBuffer.allocate(1440).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short)1440);
        buffer.putLong(48, DobotFeedbackParser.TEST_VALUE);
        buffer.putLong(8, 1L);
        buffer.putLong(24, 5L);
        buffer.put(1026, (byte)1);
        buffer.putLong(1112, 42L);
        buffer.put(1420, (byte)0xFF);
        buffer.putDouble(624, 300.0);
        return buffer.array();
    }

    private void awaitFeedback(RoboticManipulatorStreamService service) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (service.snapshot().latestFeedback() == null && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull(service.snapshot().latestFeedback());
    }

    @Test
    void receivesFeedbackWithoutDashboardAndRejectsCommandsWithoutConnectionError() throws Exception {
        try (var feedback = new ServerSocket(0); var dashboard = new ServerSocket(0)) {
            feedback.setSoTimeout(2000);
            dashboard.setSoTimeout(100);
            var settings = settings(feedback.getLocalPort(), dashboard.getLocalPort());
            var service = service(settings);
            try {
                service.startCommunication();
                try (Socket client = feedback.accept()) {
                    client.getOutputStream().write(frame());
                    awaitFeedback(service);
                    assertEquals(PeripheralWorkerState.STOPPED, service.snapshot().commandChannel().state());
                    assertEquals(300.0, service.snapshot().latestFeedback().pose().x());
                    assertEquals(ManipulatorConnectionStatus.ONLINE,
                            new ManipulatorStatusEvaluator(settings).evaluate(true, service.snapshot()).connectionStatus());
                    assertFalse(service.snapshot().hatchAnalogInput().enabled());
                    service.pollHatchAnalogInput();
                    // Saving a different mode does not affect the running session before Apply.
                    settings.setValue(SettingsService.MANIPULATOR_FEEDBACK_ONLY, false);
                    var error = assertThrows(IllegalStateException.class,
                            () -> service.executeCommand("EnableRobot()", Duration.ofSeconds(1)));
                    assertTrue(error.getMessage().contains("feedback-only"));
                    assertThrows(IllegalStateException.class,
                            () -> service.executeCommandAndAwaitCompletion("MovL(1,2,3,4,5,6)", Duration.ofSeconds(1)));
                    assertEquals("", service.snapshot().lastError());
                    assertTrue(service.snapshot().started());
                    assertThrows(SocketTimeoutException.class, dashboard::accept);
                }
            } finally {
                service.stopCommunication();
            }
        }
    }

    @Test
    void modeChangesOnRestartAndWatchdogReconnectsOnlyFeedback() throws Exception {
        try (var feedback = new ServerSocket(0); var dashboard = new ServerSocket(0)) {
            feedback.setSoTimeout(2000);
            dashboard.setSoTimeout(2000);
            var settings = settings(feedback.getLocalPort(), dashboard.getLocalPort());
            var service = service(settings);
            try {
                service.startCommunication();
                try (var client = feedback.accept()) {
                    client.getOutputStream().write(frame());
                    awaitFeedback(service);
                    settings.setValue(SettingsService.MANIPULATOR_TELEMETRY_STALE_MILLIS, 1L);
                    Thread.sleep(30);
                    service.maintainConnection();
                    assertFalse(service.snapshot().started());
                }
                settings.setValue(SettingsService.MANIPULATOR_TELEMETRY_STALE_MILLIS, 3500L);
                service.maintainConnection();
                try (var reconnected = feedback.accept()) {
                    assertTrue(service.snapshot().feedbackOnly());
                    reconnected.getOutputStream().write(frame());
                    awaitFeedback(service);
                }
                service.stopCommunication();
                settings.setValue(SettingsService.MANIPULATOR_FEEDBACK_ONLY, false);
                service.startCommunication();
                try (var command = dashboard.accept(); var stream = feedback.accept()) {
                    assertFalse(service.snapshot().feedbackOnly());
                    assertEquals(PeripheralWorkerState.RUNNING, service.snapshot().commandChannel().state());
                }
                service.stopCommunication();
                settings.setValue(SettingsService.MANIPULATOR_FEEDBACK_ONLY, true);
                service.startCommunication();
                try (var stream = feedback.accept()) {
                    assertTrue(service.snapshot().feedbackOnly());
                    assertEquals(PeripheralWorkerState.STOPPED, service.snapshot().commandChannel().state());
                }
            } finally {
                service.stopCommunication();
            }
        }
    }
}
