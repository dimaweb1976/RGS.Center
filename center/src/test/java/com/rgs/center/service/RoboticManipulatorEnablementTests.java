package com.rgs.center.service;

import com.rgs.center.connection.DeviceEnablementService;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralWorkerState;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoboticManipulatorEnablementTests {

    @Test
    void disabledManipulatorDoesNotOpenTcpChannels() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();
        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        RoboticManipulatorStreamService service =
                new RoboticManipulatorStreamService(settings, currentData);
        service.setDeviceEnablementService(new DeviceEnablementService(
                new MockEnvironment().withProperty(
                        "rgs.device.manipulator.enabled",
                        "false"
                )
        ));

        service.startCommunication();
        service.maintainConnection();

        assertFalse(service.isEnabled());
        assertEquals(
                PeripheralWorkerState.STOPPED,
                service.communicationSnapshot().state()
        );
        assertEquals(
                0L,
                currentData.getValue(
                        DeviceCode.ROBOTIC_MANIPULATOR,
                        CurrentDataService.INTERACTION_LASTDATE_IN,
                        Long.class
                )
        );
    }
}
