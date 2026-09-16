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
class WizardNewModesControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void rendersAutomaticProjectionWithoutSampleTimer() throws Exception {
        mockMvc.perform(get("/wizard-new/automatic"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Camera workfield")))
                .andExpect(content().string(containsString("No camera frame yet")))
                .andExpect(content().string(containsString("data-wn-action=\"run/start\"")))
                .andExpect(content().string(containsString("/wizard-new/css/modes.css")));
        mockMvc.perform(get("/wizard-new/automatic/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"wn-mode-workspace\"")));
    }

    @Test
    void rendersManualProjectionUsingExistingActionPaths() throws Exception {
        mockMvc.perform(get("/wizard-new/manual"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prepare a manual measurement")))
                .andExpect(content().string(containsString("data-wn-action=\"weigh\"")))
                .andExpect(content().string(containsString("Pause unavailable")));
        mockMvc.perform(get("/wizard-new/manual/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"wn-mode-workspace\"")));
        mockMvc.perform(get("/wizard-new/js/workflow-actions.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/wizard/manual")))
                .andExpect(content().string(containsString("/wizard/automatic")));
    }
}
