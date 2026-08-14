package com.rgs.center.connection;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;
import com.rgs.center.peripheral.PeripheralWorkerState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceConnectionManagerTests {

    @Test
    void startsEveryDeviceAndIsolatesConnectionFailure() {
        FakeManagedDevice camera = new FakeManagedDevice(
                DeviceCode.CAMERA,
                false
        );
        FakeManagedDevice boson = new FakeManagedDevice(
                DeviceCode.BOSON,
                true
        );
        FakeManagedDevice nicole = new FakeManagedDevice(
                DeviceCode.NICOLE,
                false
        );
        DeviceConnectionManager manager = new DeviceConnectionManager(
                List.of(camera, boson, nicole)
        );

        manager.start();

        assertTrue(camera.startAttempted.get());
        assertTrue(boson.startAttempted.get());
        assertTrue(nicole.startAttempted.get());
        assertTrue(manager.isRunning());

        manager.stop();

        assertTrue(camera.stopAttempted.get());
        assertTrue(boson.stopAttempted.get());
        assertTrue(nicole.stopAttempted.get());
        assertFalse(manager.isRunning());
    }

    @Test
    void skipsDisabledDeviceDuringStartupAndShutdown() {
        FakeManagedDevice enabled = new FakeManagedDevice(
                DeviceCode.CAMERA,
                false,
                true
        );
        FakeManagedDevice disabled = new FakeManagedDevice(
                DeviceCode.GDRM,
                false,
                false
        );
        DeviceConnectionManager manager = new DeviceConnectionManager(
                List.of(enabled, disabled)
        );

        manager.start();
        manager.stop();

        assertTrue(enabled.startAttempted.get());
        assertTrue(enabled.stopAttempted.get());
        assertFalse(disabled.startAttempted.get());
        assertFalse(disabled.stopAttempted.get());
    }

    private static final class FakeManagedDevice
            implements ManagedDeviceConnection {

        private final DeviceCode code;
        private final boolean failOnStart;
        private final boolean enabled;
        private final AtomicBoolean startAttempted = new AtomicBoolean();
        private final AtomicBoolean stopAttempted = new AtomicBoolean();

        private FakeManagedDevice(
                DeviceCode code,
                boolean failOnStart
        ) {
            this(code, failOnStart, true);
        }

        private FakeManagedDevice(
                DeviceCode code,
                boolean failOnStart,
                boolean enabled
        ) {
            this.code = code;
            this.failOnStart = failOnStart;
            this.enabled = enabled;
        }

        @Override
        public DeviceCode deviceCode() {
            return code;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void startCommunication() {
            startAttempted.set(true);
            if (failOnStart) {
                throw new IllegalStateException("Device is unavailable");
            }
        }

        @Override
        public void stopCommunication() {
            stopAttempted.set(true);
        }

        @Override
        public PeripheralWorkerSnapshot communicationSnapshot() {
            return new PeripheralWorkerSnapshot(
                    code.getCode(),
                    PeripheralWorkerState.STOPPED,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    ""
            );
        }
    }
}
