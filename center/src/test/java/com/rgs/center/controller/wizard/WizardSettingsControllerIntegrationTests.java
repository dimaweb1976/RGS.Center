package com.rgs.center.controller.wizard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        "rgs.only-one.enforce=false"
})
@AutoConfigureMockMvc
class WizardSettingsControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersSettingsOverviewWithoutReplacingLegacyPage() throws Exception {
        mockMvc.perform(get("/wizard/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Connection parameters")))
                .andExpect(content().string(containsString(
                        "href=\"/wizard/settings/RoboticManipulator\""
                )))
                .andExpect(content().string(containsString("stage 10")))
                .andExpect(content().string(containsString(
                        "/settings/device_settings/index.html"
                )));
    }

    @Test
    void rendersValidatedDeviceFormAndAuditSection() throws Exception {
        mockMvc.perform(get("/wizard/settings/barcode-scanner"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Barcode scanner")))
                .andExpect(content().string(containsString("COM port")))
                .andExpect(content().string(containsString(
                        "data-setting-id=\"device.barcode-scanner.port\""
                )))
                .andExpect(content().string(containsString("Recent setting changes")))
                .andExpect(content().string(containsString("Not saved yet")));
    }

    @Test
    void unknownSettingsDeviceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/wizard/settings/not-a-device"))
                .andExpect(status().isNotFound());
    }
}
