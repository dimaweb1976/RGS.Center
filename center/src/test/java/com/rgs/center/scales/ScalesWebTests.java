package com.rgs.center.scales;

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
        "rgs.device.scales.enabled=false",
        "rgs.device.scales.port=COM7",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class ScalesWebTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scalesPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/scales"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rendersLiveScalesPageAndStatusFragment() throws Exception {
        mockMvc.perform(get("/scales").with(user("test-operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Весы RADWAG WTC")))
                .andExpect(content().string(containsString(
                        "setInterval(loadScalesStatus, 1000)"
                )))
                .andExpect(content().string(containsString("COM7")))
                .andExpect(content().string(containsString("Вес образца")))
                .andExpect(content().string(containsString("scales-indicator")));

        mockMvc.perform(get("/scales/status").with(user("test-operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ping / связь")))
                .andExpect(content().string(containsString("Индикатор нуля")))
                .andExpect(content().string(containsString("НЕТ ДАННЫХ")));
    }
}
