package com.rgs.center.api.devices;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class DeviceRestControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatedFrontendReceivesStableJsonSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/devices").with(user("nextjs-user")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.generatedAt").isString())
                .andExpect(jsonPath("$.devices.length()").value(8))
                .andExpect(jsonPath("$.devices[0].code").value("camera"))
                .andExpect(jsonPath("$.devices[0].connectionStatus")
                        .value("DISABLED"))
                .andExpect(jsonPath("$.devices[0].enabled").value(false))
                .andExpect(jsonPath("$.devices[2].code").value("nicole"))
                .andExpect(jsonPath("$.nicole.connectionStatus")
                        .value("DISABLED"))
                .andExpect(jsonPath("$.nicole.technicalStatus")
                        .value("DISABLED"))
                .andExpect(jsonPath("$.nicole.snapshot").doesNotExist())
                .andExpect(jsonPath("$.shp.technicalStatus")
                        .value("NO_DATA"));
    }

    @Test
    void anonymousApiRequestReceivesJson401WithoutLoginRedirect() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/problem+json"
                ))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.detail", startsWith("Authentication")));
    }

    @Test
    void forbiddenApiRequestReceivesJson403() throws Exception {
        mockMvc.perform(post("/api/v1/devices").with(user("nextjs-user")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/problem+json"
                ))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"));
    }
}
