package com.rgs.center.controller.wizard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class WizardHomeControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersIndependentWizardMainMenu() throws Exception {
        mockMvc.perform(get("/wizard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(
                        "GAMMA ROBOTIC SPECTROMETER"
                )))
                .andExpect(content().string(containsString("MAIN MENU")))
                .andExpect(content().string(containsString(
                        "Automatic measurement regime"
                )))
                .andExpect(content().string(containsString(
                        "href=\"/wizard/automatic\""
                )))
                .andExpect(content().string(containsString(
                        "Manual measurement regime"
                )))
                .andExpect(content().string(containsString(
                        "href=\"/wizard/manual\""
                )))
                .andExpect(content().string(containsString("HV request")))
                .andExpect(content().string(containsString("Lead shield")))
                .andExpect(content().string(containsString(
                        "/wizard/js/status-refresh.js"
                )))
                .andExpect(content().string(containsString(
                        "Open current interface"
                )));
    }

    @Test
    void rendersWizardMainMenuWithTrailingSlash() throws Exception {
        mockMvc.perform(get("/wizard/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("MAIN MENU")));
    }

    @Test
    void returnsOnlyLiveStatusFragmentForPolling() throws Exception {
        mockMvc.perform(get("/wizard/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(
                        "id=\"wizard-live-status\""
                )))
                .andExpect(content().string(containsString("Robot")))
                .andExpect(content().string(containsString("No data")))
                .andExpect(content().string(not(containsString(
                        "Select operating mode"
                ))));
    }

    @Test
    void keepsExistingHomePageAvailable() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(
                        "RGSCenter Wizard — stage 1"
                ))));
    }
}
