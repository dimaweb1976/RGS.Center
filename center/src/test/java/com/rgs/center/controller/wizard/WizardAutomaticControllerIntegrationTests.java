package com.rgs.center.controller.wizard;

import com.rgs.center.camera.CameraImagePayload;
import com.rgs.center.camera.CameraImagingService;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.camera.DetectedBottle;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.measurement.workflow.MeasurementObjectRequest;
import com.rgs.center.measurement.workflow.MeasurementObjectSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowState;
import com.rgs.center.measurement.workflow.MeasurementWorkflowStep;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
class WizardAutomaticControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeasurementParametersService parametersService;

    @MockitoBean
    private CameraImagingService cameraImaging;

    @MockitoBean
    private ProductionMeasurementOrchestrator orchestrator;

    @BeforeEach
    void prepareCamera() {
        reset(cameraImaging, orchestrator);
        when(cameraImaging.snapshot()).thenReturn(emptyCamera());
        when(orchestrator.snapshot()).thenReturn(idleWorkflow());
    }

    @Test
    void rendersPreparationPageWithStartDisabledUntilRecognition()
            throws Exception {
        mockMvc.perform(get("/wizard/automatic"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString(
                        "AUTO MEASUREMENTS — PREPARATION"
                )))
                .andExpect(content().string(containsString("Camera image")))
                .andExpect(content().string(containsString("Capture image")))
                .andExpect(content().string(containsString(
                        "data-wizard-action=\"run/start\" disabled"
                )));
    }

    @Test
    void captureReturnsUpdatedThymeleafWorkspace() throws Exception {
        CameraImagingSnapshot captured = new CameraImagingSnapshot(
                true,
                42L,
                false,
                "New frame",
                "",
                "",
                List.of()
        );
        when(cameraImaging.capture()).thenReturn(captured);
        when(cameraImaging.snapshot()).thenReturn(captured);

        mockMvc.perform(post("/wizard/automatic/camera/capture").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"wizard-automatic-workspace\""
                )))
                .andExpect(content().string(containsString("New frame")))
                .andExpect(content().string(containsString(
                        "/wizard/automatic/camera/image?version=42"
                )));

        verify(cameraImaging).capture();
    }

    @Test
    void recognitionRendersDetectedObjects() throws Exception {
        CameraImagingSnapshot recognized = new CameraImagingSnapshot(
                true,
                43L,
                true,
                "Recognition completed",
                "C:\\camera\\bottles.jpeg",
                "C:\\camera\\bottles_detected.jpeg",
                List.of(new DetectedBottle(1, 125.5, 64.0, 40, 42, 41.0, 0.91))
        );
        when(cameraImaging.recognize()).thenReturn(recognized);
        when(cameraImaging.snapshot()).thenReturn(recognized);

        mockMvc.perform(post("/wizard/automatic/camera/recognize").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recognition completed")))
                .andExpect(content().string(containsString("1 objects")))
                .andExpect(content().string(containsString("125.5")))
                .andExpect(content().string(containsString(
                        "C:\\camera\\bottles_detected.jpeg"
                )));

        verify(cameraImaging).recognize();
    }

    @Test
    void servesOnlyTheRequestedImageVersion() throws Exception {
        when(cameraImaging.currentImage()).thenReturn(Optional.of(
                new CameraImagePayload(new byte[]{1, 2, 3}, "image/jpeg", 77L)
        ));

        mockMvc.perform(get("/wizard/automatic/camera/image?version=77"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/wizard/automatic/camera/image?version=78"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rendersLiveAutomaticProgressFromOrchestratorSnapshot()
            throws Exception {
        when(orchestrator.snapshot()).thenReturn(runningWorkflow());
        mockMvc.perform(get("/wizard/automatic/progress"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "AUTO MEASUREMENTS — MONITOR"
                )))
                .andExpect(content().string(containsString(
                        "Automatic measurement is running"
                )))
                .andExpect(content().string(containsString(
                        "Read and validate barcode"
                )))
                .andExpect(content().string(containsString(
                        "data-workflow-action=\"pause\""
                )));
    }

    @Test
    void startsProductionRunWithRecognizedBottleCoordinates() throws Exception {
        CameraImagingSnapshot recognized = recognizedCamera();
        when(cameraImaging.snapshot()).thenReturn(recognized);
        when(orchestrator.start(anyList())).thenReturn(
                UUID.fromString("8f6ddc93-8468-4297-b4c9-8a11ab990eba")
        );

        mockMvc.perform(post("/wizard/automatic/run/start").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(content().string("8f6ddc93-8468-4297-b4c9-8a11ab990eba"));

        verify(orchestrator).start(List.of(new MeasurementObjectRequest(1, 125.5, 64.0)));
    }

    @Test
    void rejectsStartWithoutRecognizedObjects() throws Exception {
        mockMvc.perform(post("/wizard/automatic/run/start").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Recognize at least one bottle")));
    }

    @Test
    void exposesPauseResumeAndSafeStopControls() throws Exception {
        when(orchestrator.pause()).thenReturn(true);
        when(orchestrator.resume()).thenReturn(true);
        when(orchestrator.cancel()).thenReturn(true);

        mockMvc.perform(post("/wizard/automatic/run/pause").with(csrf()))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/wizard/automatic/run/resume").with(csrf()))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/wizard/automatic/run/stop").with(csrf()))
                .andExpect(status().isAccepted());

        verify(orchestrator).pause();
        verify(orchestrator).resume();
        verify(orchestrator).cancel();
    }

    @Test
    void returnsOnlyLiveRunMonitorFragmentForPolling() throws Exception {
        when(orchestrator.snapshot()).thenReturn(runningWorkflow());

        mockMvc.perform(get("/wizard/automatic/run/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"wizard-run-monitor\"")))
                .andExpect(content().string(containsString("Active object")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("<!DOCTYPE html>")
                )));
    }

    @Test
    void rendersManualModeUsingExistingDeviceServiceEndpoints()
            throws Exception {
        mockMvc.perform(get("/wizard/manual"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MANUAL MEASUREMENTS")))
                .andExpect(content().string(containsString(
                        "Manual device control"
                )))
                .andExpect(content().string(containsString(
                        "href=\"/gammapro-boson\""
                )))
                .andExpect(content().string(containsString(
                        "href=\"/barcode-scanner\""
                )))
                .andExpect(content().string(containsString(
                        "href=\"/scales\""
                )))
                .andExpect(content().string(containsString(
                        "href=\"/manipulator\""
                )))
                .andExpect(content().string(containsString(
                        "Production manual measurement is not enabled yet"
                )));
    }

    @Test
    void rendersPersistedMeasurementParameters() throws Exception {
        MeasurementParameters current = parametersService.current();

        mockMvc.perform(get("/wizard/measurement-parameters"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Parameters applied to the next measurement"
                )))
                .andExpect(content().string(containsString(
                        "name=\"measuringTimeMinutes\""
                )))
                .andExpect(content().string(containsString(
                        "value=\"" + current.version() + "\""
                )));
    }

    @Test
    void savesValidMeasurementParametersAndIncrementsVersion()
            throws Exception {
        MeasurementParameters before = parametersService.current();

        mockMvc.perform(post("/wizard/measurement-parameters")
                        .with(csrf())
                        .param("measuringTimeMinutes", "45")
                        .param("volumeMl", "750.5")
                        .param("geometry", "VIAL")
                        .param("tareMassGrams", "123.25")
                        .param("version", Long.toString(before.version())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Measurement parameters saved"
                )))
                .andExpect(content().string(containsString("value=\"45\"")));

        MeasurementParameters saved = parametersService.current();
        assertEquals(45, saved.measuringTimeMinutes());
        assertEquals(750.5, saved.volumeMl());
        assertEquals(SampleGeometry.VIAL, saved.geometry());
        assertEquals(123.25, saved.tareMassGrams());
        assertEquals(before.version() + 1, saved.version());

        mockMvc.perform(get("/wizard/automatic"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("45 min")))
                .andExpect(content().string(containsString("750.5 ml")))
                .andExpect(content().string(containsString("Vial")));
        mockMvc.perform(get("/wizard/manual"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("123.25 g")));
    }

    @Test
    void rejectsInvalidMeasurementParametersWithoutDatabaseUpdate()
            throws Exception {
        MeasurementParameters before = parametersService.current();

        mockMvc.perform(post("/wizard/measurement-parameters")
                        .with(csrf())
                        .param("measuringTimeMinutes", "0")
                        .param("volumeMl", "300")
                        .param("geometry", "BOTTLE")
                        .param("tareMassGrams", "0")
                        .param("version", Long.toString(before.version())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Measuring time must be between 1 and 1440 minutes"
                )));

        assertEquals(before, parametersService.current());
    }

    private static CameraImagingSnapshot emptyCamera() {
        return new CameraImagingSnapshot(
                false,
                0L,
                false,
                "No camera frame yet.",
                "",
                "",
                List.of()
        );
    }

    private static CameraImagingSnapshot recognizedCamera() {
        return new CameraImagingSnapshot(
                true, 43L, true, "Recognition completed",
                "C:\\camera\\bottles.jpeg",
                "C:\\camera\\bottles_detected.jpeg",
                List.of(new DetectedBottle(1, 125.5, 64.0, 40, 42, 41.0, 0.91))
        );
    }

    private static MeasurementWorkflowSnapshot idleWorkflow() {
        return new MeasurementWorkflowSnapshot(
                null, MeasurementWorkflowState.IDLE, null, null,
                null, null, null, "", List.of(), List.of()
        );
    }

    private static MeasurementWorkflowSnapshot runningWorkflow() {
        return new MeasurementWorkflowSnapshot(
                UUID.fromString("8f6ddc93-8468-4297-b4c9-8a11ab990eba"),
                MeasurementWorkflowState.RUNNING,
                MeasurementWorkflowStep.READ_BARCODE,
                1,
                java.time.Instant.EPOCH,
                null,
                new MeasurementParameters(
                        1, 300, SampleGeometry.BOTTLE, 25, 1, java.time.Instant.EPOCH
                ),
                "",
                List.of(new MeasurementObjectSnapshot(
                        1, 125.5, 64.0, "RUNNING",
                        MeasurementWorkflowStep.MOVE_TO_BARCODE,
                        "", null, null, "", ""
                )),
                List.of()
        );
    }
}
