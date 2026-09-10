package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingType;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSettingsServiceTests {

    private DeviceSettingsRepository repository;
    private SettingsService runtimeSettings;
    private DeviceSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(DeviceSettingsRepository.class);
        runtimeSettings = new SettingsService();
        service = new DeviceSettingsService(
                repository,
                new DeviceSettingCatalog(),
                runtimeSettings
        );
    }

    @Test
    void savesWithAdministratorIdAndRefreshesRuntimeValue() {
        DeviceSetting saved = new DeviceSetting(
                SettingsService.SCALES_PORT,
                "scales",
                SettingType.STRING,
                "COM12",
                1,
                Instant.now(),
                7L,
                "admin"
        );
        when(repository.update(
                "scales",
                SettingsService.SCALES_PORT,
                SettingType.STRING,
                "COM12",
                0,
                7
        )).thenReturn(1);
        when(repository.findByDevice("scales")).thenReturn(List.of(saved));

        service.updateDevice(
                "scales",
                List.of(new DeviceSettingsService.Update(
                        SettingsService.SCALES_PORT,
                        "COM12",
                        0
                )),
                7
        );

        assertEquals(
                "COM12",
                runtimeSettings.getValue(SettingsService.SCALES_PORT)
        );
        verify(repository).update(
                "scales",
                SettingsService.SCALES_PORT,
                SettingType.STRING,
                "COM12",
                0,
                7
        );
    }

    @Test
    void reportsConflictForStaleVersion() {
        when(repository.update(
                "scales",
                SettingsService.SCALES_PORT,
                SettingType.STRING,
                "COM12",
                3,
                7
        )).thenReturn(0);

        assertThrows(DeviceSettingsConflictException.class, () ->
                service.updateDevice(
                        "scales",
                        List.of(new DeviceSettingsService.Update(
                                SettingsService.SCALES_PORT,
                                "COM12",
                                3
                        )),
                        7
                ));
    }
}
