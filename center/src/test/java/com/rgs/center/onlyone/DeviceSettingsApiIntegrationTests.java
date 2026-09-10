package com.rgs.center.onlyone;

import com.rgs.center.auth.AuthPrincipal;
import com.rgs.center.connection.DeviceConnectionManager;
import com.rgs.center.objects.SettingType;
import com.rgs.center.device.settings.DeviceSetting;
import com.rgs.center.device.settings.DeviceSettingsService;
import jakarta.servlet.http.Cookie;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rgs.device.camera.enabled=false",
        "rgs.device.lenovo-pc.enabled=false",
        "rgs.device.nicole.enabled=false",
        "rgs.device.boson.enabled=false",
        "rgs.device.manipulator.enabled=false",
        "rgs.device.barcode-scanner.enabled=false",
        "rgs.device.gdrm.enabled=false",
        "rgs.device.scales.enabled=false",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false",
        "rgs.only-one.enforce=true"
})
@AutoConfigureMockMvc
class DeviceSettingsApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceSettingsService settingsService;

    @MockitoBean
    private DeviceConnectionManager connectionManager;

    @MockitoBean
    private OnlyOneLockService lockService;

    @BeforeEach
    void setUp() {
        reset(settingsService, connectionManager, lockService);
    }

    @Test
    void readRequiresAnApplicationPrincipal() throws Exception {
        mockMvc.perform(get("/api/device-settings/scales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void updateRequiresCsrfAndExclusiveLockBeforeSaving() throws Exception {
        String body = """
                {"settings":[{
                  "stringId":"device.scales.port",
                  "value":"COM12",
                  "version":0
                }]}
                """;
        var admin = authentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(42, "admin", "administrator"),
                null,
                List.of()
        ));

        mockMvc.perform(put("/api/device-settings/scales")
                        .with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        when(settingsService.findByDevice("scales")).thenReturn(List.of());
        Cookie csrfCookie = mockMvc.perform(get("/api/device-settings/scales")
                        .with(admin))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);

        mockMvc.perform(put("/api/device-settings/scales")
                        .with(admin)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isLocked());

        when(lockService.checkMe("active-lock")).thenReturn(Optional.of(
                LocalDateTime.now().plusMinutes(5)
        ));
        when(settingsService.updateDevice(
                org.mockito.ArgumentMatchers.eq("scales"),
                anyList(),
                org.mockito.ArgumentMatchers.eq(42L)
        )).thenReturn(List.of(new DeviceSetting(
                SettingsService.SCALES_PORT,
                "scales",
                SettingType.STRING,
                "COM12",
                1,
                Instant.now(),
                42L,
                "admin"
        )));

        mockMvc.perform(put("/api/device-settings/scales")
                        .with(admin)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("X-Lock-Token", "active-lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings[0].value").value("COM12"))
                .andExpect(jsonPath("$.settings[0].version").value(1));

        verify(settingsService).updateDevice(
                org.mockito.ArgumentMatchers.eq("scales"),
                anyList(),
                org.mockito.ArgumentMatchers.eq(42L)
        );
    }
}
