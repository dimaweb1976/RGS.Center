package com.rgs.center.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Проверяет защищённую HTML-страницу ручного управления GammaPRO/BOSON. */
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
class GammaProBosonWebIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pageShowsStatusesCommandsAndPolling() throws Exception {
        mockMvc.perform(get("/gammapro-boson").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "GammaPro_BOSON"
                )))
                .andExpect(content().string(containsString(
                        "connection-status DISCONNECTED"
                )))
                .andExpect(content().string(containsString(
                        "boson-process-status DISCONNECTED"
                )))
                .andExpect(content().string(containsString(
                        "SET_TRACT(BOSON,Ch1)"
                )))
                .andExpect(content().string(containsString(
                        "SET_HV(1,1000,0)"
                )))
                .andExpect(content().string(containsString(
                        "SET_HV(0,1000,0)"
                )))
                .andExpect(content().string(containsString(
                        "setInterval(loadGammaProBosonStatus, 10000)"
                )))
                .andExpect(content().string(containsString(
                        "window.rgsCsrfHeaders"
                )));
    }

    @Test
    void menuContainsGammaProBosonLink() throws Exception {
        mockMvc.perform(get("/devices").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"/gammapro-boson\""
                )));
    }

    @Test
    void statusFragmentRendersWithoutSocket() throws Exception {
        mockMvc.perform(get("/gammapro-boson/status")
                        .with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "GammaPRO socket"
                )))
                .andExpect(content().string(containsString("disabled")));
    }

    @Test
    void postRequiresCsrfAndReturnsHandledConnectionError() throws Exception {
        mockMvc.perform(post("/gammapro-boson/tract")
                        .with(user("operator")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/gammapro-boson/tract")
                        .with(user("operator"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action-message error"
                )));
    }
}
