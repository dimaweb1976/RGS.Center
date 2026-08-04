package com.rgs.center.service;

import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.objects.DeviceCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoboticManipulatorStreamServiceTests {

    @Test
    void recordsLastCheckWhenInitialConnectionFails() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();
        settings.setValue(SettingsService.MANIPULATOR_HOST, "127.0.0.1");
        settings.setValue(SettingsService.MANIPULATOR_COMMAND_PORT, 1L);
        settings.setValue(
                SettingsService.MANIPULATOR_CONNECT_TIMEOUT_MILLIS,
                20L
        );
        settings.setValue(SettingsService.MANIPULATOR_READ_TIMEOUT_MILLIS, 20L);
        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        RoboticManipulatorStreamService service =
                new RoboticManipulatorStreamService(settings, currentData);

        assertThrows(RuntimeException.class, service::startCommunication);

        assertTrue(currentData.getValue(
                DeviceCode.ROBOTIC_MANIPULATOR,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        ) > 0L);
    }
}
