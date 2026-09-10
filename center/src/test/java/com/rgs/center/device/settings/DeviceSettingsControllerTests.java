package com.rgs.center.device.settings;

import com.rgs.center.auth.AuthPrincipal;
import com.rgs.center.connection.DeviceConnectionManager;
import com.rgs.center.connection.DeviceReconfigurationResult;
import com.rgs.center.objects.SettingType;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSettingsControllerTests {

    private final DeviceSettingsService service = mock(DeviceSettingsService.class);
    private final DeviceConnectionManager manager = mock(DeviceConnectionManager.class);
    private final DeviceSettingsController controller =
            new DeviceSettingsController(service, manager);

    @Test
    void administratorIdIsRecordedOnUpdate() {
        DeviceSetting saved = new DeviceSetting(
                SettingsService.SCALES_PORT,
                "scales",
                SettingType.STRING,
                "COM12",
                1,
                Instant.now(),
                42L,
                "admin"
        );
        List<DeviceSettingsService.Update> expected = List.of(
                new DeviceSettingsService.Update(
                        SettingsService.SCALES_PORT,
                        "COM12",
                        0
                )
        );
        when(service.updateDevice("scales", expected, 42))
                .thenReturn(List.of(saved));

        var response = controller.update(
                "scales",
                new DeviceSettingsController.UpdateRequest(List.of(
                        new DeviceSettingsController.UpdateValue(
                                SettingsService.SCALES_PORT,
                                "COM12",
                                0
                        )
                )),
                authentication(42, "admin", "administrator")
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).updateDevice("scales", expected, 42);
    }

    @Test
    void operatorCanReadButCannotSaveOrApply() {
        var operator = authentication(11, "operator", "operator");
        when(service.findByDevice("scales")).thenReturn(List.of());

        assertEquals(
                HttpStatus.OK,
                controller.device("scales", operator).getStatusCode()
        );
        assertEquals(
                HttpStatus.FORBIDDEN,
                controller.update(
                        "scales",
                        new DeviceSettingsController.UpdateRequest(List.of()),
                        operator
                ).getStatusCode()
        );
        assertEquals(
                HttpStatus.FORBIDDEN,
                controller.apply("scales", operator).getStatusCode()
        );
    }

    @Test
    void applyReturnsServiceUnavailableWhenReconnectFails() {
        when(manager.reconfigure("scales")).thenReturn(
                new DeviceReconfigurationResult(
                        "scales",
                        false,
                        "FAILED",
                        "Cannot open COM12"
                )
        );

        var response = controller.apply(
                "scales",
                authentication(42, "admin", "administrator")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    private static UsernamePasswordAuthenticationToken authentication(
            long id,
            String login,
            String role
    ) {
        return new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(id, login, role),
                null,
                List.of()
        );
    }
}
