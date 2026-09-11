package com.rgs.center.api.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
class DashboardSnapshotControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousClientReceivesVersionedAggregatedSnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/snapshot"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/json"
                ))
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.equipment.collectedAt").isString())
                .andExpect(jsonPath("$.equipment.cameras.length()").value(1))
                .andExpect(jsonPath(
                        "$.equipment.cameras[0].health.connection"
                ).value("DISABLED"))
                .andExpect(jsonPath(
                        "$.equipment.cameras[0].health.endpoint"
                ).isString())
                .andExpect(jsonPath(
                        "$.equipment.lenovoPc.connection"
                ).value("DISABLED"))
                .andExpect(jsonPath(
                        "$.equipment.nicole.health.technical"
                ).value("DISABLED"))
                .andExpect(jsonPath(
                        "$.equipment.boson.health.connection"
                ).value("DISABLED"))
                .andExpect(jsonPath(
                        "$.equipment.manipulator.health.connection"
                ).value("DISABLED"));
    }

    @Test
    void thymeleafDevicesModelContainsTheSameSnapshotType() throws Exception {
        mockMvc.perform(get("/devices"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("dashboardSnapshot"))
                .andExpect(model().attributeDoesNotExist(
                        "devices",
                        "nicoleStatus",
                        "shpStatus",
                        "gammaProStatus",
                        "manipulatorStatus"
                ))
                .andExpect(content().string(containsString("Gamma-PRO")))
                .andExpect(content().string(containsString("Nicole state")));
    }
}
