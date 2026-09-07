package com.rgs.center.api.manipulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class ManipulatorRestControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStableDisabledSnapshotForSeparateFrontend() throws Exception {
        mockMvc.perform(get("/api/v1/manipulator/status")
                        .with(user("test-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.feedbackPort").value(30006))
                .andExpect(jsonPath("$.connectionStatus").value("DISABLED"))
                .andExpect(jsonPath("$.technicalStatus").value("DISABLED"))
                .andExpect(jsonPath("$.pose.x").isEmpty())
                .andExpect(jsonPath("$.hatch.analogEnabled").value(false))
                .andExpect(jsonPath("$.telemetry.feedbackChannelState")
                        .value("STOPPED"));
    }

    @Test
    void unauthenticatedRequestUsesJson401Contract() throws Exception {
        mockMvc.perform(get("/api/v1/manipulator/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
