package com.rgs.center.controller.wizard;

import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class WizardServiceDevicesControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeasurementExecutionCoordinator coordinator;

    @Test
    void rendersUnifiedOverviewIncludingGdrm() throws Exception {
        mockMvc.perform(get("/wizard/devices"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("SERVICE - DEVICES")))
                .andExpect(content().string(containsString("Device overview")))
                .andExpect(content().string(containsString("GDRM")))
                .andExpect(content().string(containsString(
                        "href=\"/wizard/devices/scales\""
                )))
                .andExpect(content().string(containsString("Technical")))
                .andExpect(content().string(containsString("Last response")));
    }

    @Test
    void rendersDetailedScalesStateAndWorkerQueues() throws Exception {
        mockMvc.perform(get("/wizard/devices/scales"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RADWAG SI")))
                .andExpect(content().string(containsString("Read current weight")))
                .andExpect(content().string(containsString(
                        "Connection, queues and protocol traffic"
                )))
                .andExpect(content().string(containsString("TX queue")))
                .andExpect(content().string(containsString("Dropped")));
    }

    @Test
    void rendersBothDobotTransportChannels() throws Exception {
        mockMvc.perform(get("/wizard/devices/roboticmanipulator"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Dobot command 29999 / feedback 30006"
                )))
                .andExpect(content().string(containsString("Dobot command channel")))
                .andExpect(content().string(containsString("Dobot feedback channel")));
    }

    @Test
    void everyConfiguredDeviceHasAWorkingDetailPage() throws Exception {
        for (String code : List.of(
                "camera", "lenovo-pc", "nicole", "boson",
                "roboticmanipulator", "barcode-scanner", "scales",
                "shp", "gamma-pro", "gdrm"
        )) {
            mockMvc.perform(get("/wizard/devices/{code}", code))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "id=\"wizard-service-content\""
                    )));
        }
    }

    @Test
    void unknownDeviceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/wizard/devices/not-a-device"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pollingReturnsOnlyServiceFragment() throws Exception {
        mockMvc.perform(get("/wizard/devices/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"wizard-service-content\""
                )))
                .andExpect(content().string(not(containsString("<title>"))));
    }

    @Test
    void deviceFailureIsRenderedWithoutHttp500() throws Exception {
        mockMvc.perform(post("/wizard/devices/scales/actions/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Read current weight is unavailable in the current device state"
                )))
                .andExpect(content().string(containsString(
                        "wizard-action-message--error"
                )));
    }

    @Test
    void serviceCommandIsBlockedWhileAutomaticWorkflowOwnsEquipment()
            throws Exception {
        coordinator.acquire(MeasurementExecutionCoordinator.Owner.AUTOMATIC);
        try {
            mockMvc.perform(post("/wizard/devices/scales/actions/read").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "Measurement equipment is already reserved by AUTOMATIC"
                    )));
        } finally {
            coordinator.release(MeasurementExecutionCoordinator.Owner.AUTOMATIC);
        }
    }
}
