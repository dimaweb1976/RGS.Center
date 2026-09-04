package com.rgs.center.barcodescanner;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rgs.device.camera.enabled=false",
        "rgs.device.lenovo-pc.enabled=false",
        "rgs.device.nicole.enabled=false",
        "rgs.device.boson.enabled=false",
        "rgs.device.manipulator.enabled=false",
        "rgs.device.barcode-scanner.enabled=false",
        "rgs.device.barcode-scanner.port=COM8",
        "rgs.device.barcode-scanner.baud-rate=9600",
        "rgs.device.gdrm.enabled=false",
        "rgs.device.scales.enabled=false",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false"
})
@AutoConfigureMockMvc
class BarcodeScannerWebTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scannerPageRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/barcode-scanner"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void rendersScannerPageAndStatusWithConfiguredSerialParameters()
            throws Exception {
        mockMvc.perform(get("/barcode-scanner").with(user("test-service")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Сканер штрих-кодов Honeywell Vuquest 3320"
                )))
                .andExpect(content().string(containsString("COM8")))
                .andExpect(content().string(containsString("9600")))
                .andExpect(content().string(containsString(
                        "window.setInterval(loadScannerStatus, 1000)"
                )));

        mockMvc.perform(get("/barcode-scanner/status")
                        .with(user("test-service")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DISABLED")))
                .andExpect(content().string(containsString(
                        "Считать штрих-код"
                )));
    }

    @Test
    void scannerActionsRequireCsrfAndReturnStatusFragment() throws Exception {
        mockMvc.perform(post("/barcode-scanner/read")
                        .with(user("test-service")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/barcode-scanner/cancel")
                        .with(user("test-service"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Последний штрих-код")));
    }
}
