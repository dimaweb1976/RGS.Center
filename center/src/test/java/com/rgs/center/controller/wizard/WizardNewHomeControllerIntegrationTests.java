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
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class WizardNewHomeControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void rendersIndependentShellWithoutSimulatedMeasurement() throws Exception {
        mockMvc.perform(get("/wizard-new"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Choose a section")))
                .andExpect(content().string(containsString("Open automatic mode")))
                .andExpect(content().string(containsString("href=\"/wizard-new/automatic\"")))
                .andExpect(content().string(containsString("href=\"/wizard-new/database\"")))
                .andExpect(content().string(containsString("href=\"/wizard\"")))
                .andExpect(content().string(containsString("/wizard-new/css/shell.css")))
                .andExpect(content().string(containsString("/wizard-new/js/shell.js")));
        mockMvc.perform(get("/wizard-new/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"wn-live-status\"")))
                .andExpect(content().string(containsString("data-workflow-state=\"IDLE\"")))
                .andExpect(content().string(containsString("PLC")))
                .andExpect(content().string(containsString("MCA")))
                .andExpect(content().string(containsString("Gamma-PRO")))
                .andExpect(content().string(containsString("No data")));
    }

    @Test
    void rendersTrailingSlashAndStaticAssets() throws Exception {
        mockMvc.perform(get("/wizard-new/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WizardNew")));
        mockMvc.perform(get("/wizard-new/css/shell.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".wn-card-grid")));
        mockMvc.perform(get("/wizard-new/js/shell.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/wizard-new/status")));
    }
}
