package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import com.rgs.center.gammapro.BosonHighVoltageSnapshot;
import com.rgs.center.gammapro.GammaProCommandResult;
import com.rgs.center.gammapro.GammaProHighVoltageControl;
import com.rgs.center.gammapro.GammaProHighVoltageValueType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BosonServiceTests {

    @Test
    void returnsBosonFromItsOwnService() {
        SettingsService settings = new SettingsService();
        settings.initializeDefaults();

        CurrentDataService currentData = new CurrentDataService();
        currentData.initializeDevices();
        currentData.setValue(
                DeviceCode.BOSON,
                CurrentDataService.PINGED,
                true
        );
        DevicePingService pingService = new DevicePingService(
                settings,
                currentData,
                (address, timeout) -> true
        );

        RecordingControl control = new RecordingControl();
        BosonService service = new BosonService(
                new DeviceViewService(settings, currentData),
                pingService,
                control
        );
        Device boson = service.getBoson();

        assertEquals("BOSON", boson.getName());
        assertEquals("192.168.1.16", boson.getIpAddress());
        assertEquals(DeviceStatus.ONLINE, boson.getStatus());
        assertEquals(
                2500,
                service.enableHighVoltage(
                        "BOSON1",
                        "GCD-4020",
                        2500
                ).requestedValue()
        );
        service.selectTract("BOSON", "Ch1");
        BosonHighVoltageSnapshot disabled = service.disableHighVoltage(
                "BOSON",
                "Ch1"
        );
        assertEquals(1000, disabled.requestedValue());
        assertEquals(false, disabled.requestedEnabled());
        assertEquals("SET_TRACT(BOSON,Ch1)", control.lastSelectedCommand);
    }

    /** Минимальный порт-заглушка, фиксирующий точные параметры делегирования. */
    private static final class RecordingControl
            implements GammaProHighVoltageControl {

        private String lastSelectedCommand = "";

        @Override
        public GammaProCommandResult selectTract(
                String mcaName,
                String channelName
        ) {
            lastSelectedCommand = "SET_TRACT(" + mcaName + ","
                    + channelName + ")";
            return new GammaProCommandResult(
                    lastSelectedCommand,
                    java.util.List.of(),
                    "SET_TRACT=OK"
            );
        }

        @Override
        public BosonHighVoltageSnapshot applyBosonHighVoltage(
                String mcaName,
                String channelName,
                boolean enabled,
                int value,
                GammaProHighVoltageValueType valueType
        ) {
            return new BosonHighVoltageSnapshot(
                    mcaName,
                    channelName,
                    enabled,
                    value,
                    valueType,
                    true,
                    1L,
                    ""
            );
        }
    }
}
