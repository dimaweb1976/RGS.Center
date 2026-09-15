package com.rgs.center.controller.wizard;

import com.rgs.center.camera.CameraImagingService;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.measurement.stepdiagnostic.StepDiagnosticService;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
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
class WizardStepDiagnosticControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private StepDiagnosticService service;
    @MockitoBean private CameraImagingService cameraImaging;
    @MockitoBean private ProductionMeasurementOrchestrator orchestrator;

    @BeforeEach
    void prepare() {
        reset(cameraImaging, orchestrator);
        when(cameraImaging.snapshot()).thenReturn(new CameraImagingSnapshot(
                false, 0, false, "No image", "", "", List.of()
        ));
    }

    @Test
    void createsPlaceholderSessionAndSkipsOnlyTheNextStep() throws Exception {
        mockMvc.perform(get("/wizard/automatic/step-test"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("STEP DIAGNOSTIC")))
                .andExpect(content().string(containsString("test placeholder")));

        mockMvc.perform(post("/wizard/automatic/step-test/session").with(csrf()))
                .andExpect(status().isAccepted());
        var session = service.snapshot().orElseThrow();

        mockMvc.perform(post("/wizard/automatic/step-test/step")
                        .param("sessionId", session.id().toString())
                        .param("objectNumber", "1")
                        .param("step", "MOVE_TO_BARCODE").with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/wizard/automatic/step-test/step")
                        .param("sessionId", session.id().toString())
                        .param("objectNumber", "1")
                        .param("step", "PICK_OBJECT").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("SKIPPED"));

        mockMvc.perform(get("/wizard/automatic/step-test/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SKIPPED")))
                .andExpect(content().string(containsString("No command sent")));
    }
}
