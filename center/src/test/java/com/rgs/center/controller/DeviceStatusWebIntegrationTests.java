package com.rgs.center.controller;

import com.rgs.center.measurement.emulator.MeasurementEmulatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rgs.device.camera.enabled=false",
        "rgs.device.lenovo-pc.enabled=false",
        "rgs.device.nicole.enabled=false",
        "rgs.device.boson.enabled=false",
        "rgs.device.manipulator.enabled=false",
        "rgs.device.barcode-scanner.enabled=false",
        "rgs.device.gdrm.enabled=false",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class DeviceStatusWebIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeasurementEmulatorService measurementEmulatorService;

    @BeforeEach
    void resetScenarioStatus() {
        measurementEmulatorService.reset();
    }

    @Test
    void devicesPageRendersDeviceDetailTablesBeforeFirstSuccessfulPoll()
            throws Exception {
        mockMvc.perform(get("/devices").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nicole state")))
                .andExpect(content().string(containsString("GammaPro State")))
                .andExpect(content().string(containsString(
                        "Measurement state"
                )))
                .andExpect(content().string(containsString(
                        "Available tracts"
                )))
                .andExpect(content().string(containsString("BOSON status")))
                .andExpect(content().string(containsString(
                        "boson-status DISCONNECTED"
                )))
                .andExpect(content().string(containsString("gamma-pro-row")))
                .andExpect(content().string(containsString(
                        "gamma-pro-status DISCONNECTED"
                )))
                .andExpect(content().string(containsString("Cryocooler")))
                .andExpect(content().string(containsString("Scenario status")))
                .andExpect(content().string(containsString(
                        "scenario-status--normal"
                )))
                .andExpect(content().string(containsString("NORMAL")))
                .andExpect(content().string(containsString(
                        "nicole-status--disabled"
                )))
                .andExpect(content().string(containsString(
                        "nicole-connection--disabled"
                )))
                .andExpect(content().string(containsString("SHP measurements")))
                .andExpect(content().string(containsString(
                        "Charge circuit output voltage"
                )))
                .andExpect(content().string(containsString(
                        "shp-status--no-data"
                )))
                .andExpect(content().string(containsString("No data")));
    }

    @Test
    void existingThymeleafPageAndPollingFragmentRemainCompatible()
            throws Exception {
        mockMvc.perform(get("/devices"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/devices").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(
                        "setInterval(loadStatus, 10000)"
                )));

        mockMvc.perform(get("/devices/status").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(
                        "Nicole state"
                )))
                .andExpect(content().string(containsString(
                        "GammaPro State"
                )))
                .andExpect(content().string(containsString(
                        "gamma-pro-status DISCONNECTED"
                )));
    }

    @Test
    void devicesPageShowsEmergencyScenarioStatusAfterScenarioAlert()
            throws Exception {
        measurementEmulatorService.toggleCheck("nicole-ok");

        mockMvc.perform(get("/devices/status").with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "scenario-status--emergency"
                )))
                .andExpect(content().string(containsString("EMERGENCY")));
    }
}
