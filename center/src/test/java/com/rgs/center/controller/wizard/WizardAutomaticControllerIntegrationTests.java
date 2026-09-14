package com.rgs.center.controller.wizard;

import com.rgs.center.camera.CameraImagePayload;
import com.rgs.center.camera.CameraImagingService;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.camera.DetectedBottle;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.manual.ManualMeasurementService;
import com.rgs.center.measurement.manual.ManualMeasurementSnapshot;
import com.rgs.center.measurement.manual.ManualMeasurementState;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.measurement.safety.ScenarioIncidentService;
import com.rgs.center.measurement.safety.ScenarioIncidentSeverity;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

    @Autowired
    private ScenarioIncidentService incidentService;

    @MockitoBean
    private CameraImagingService cameraImaging;

    @MockitoBean
    private ProductionMeasurementOrchestrator orchestrator;

    @MockitoBean
    private ManualMeasurementService manualMeasurementService;

    @BeforeEach
    void prepareCamera() {
        reset(cameraImaging, orchestrator, manualMeasurementService);
        when(cameraImaging.snapshot()).thenReturn(emptyCamera());
        when(orchestrator.snapshot()).thenReturn(idleWorkflow());
        when(manualMeasurementService.snapshot()).thenReturn(idleManualMeasurement());
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
                .andExpect(content().string(containsString("Cycle body")))
                .andExpect(content().string(containsString(
                        "wizard-cycle-step--completed"
                )))
                .andExpect(content().string(containsString(
                        "wizard-cycle-step--running"
                )))
                .andExpect(content().string(containsString(
                        "wizard-cycle-step--waiting"
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
    void rendersAndAcknowledgesSafetyIncidentWithOperatorIdentity() throws Exception {
        incidentService.raise(
                ScenarioIncidentSeverity.EMERGENCY,
                "shp",
                "SHP_TECHNICAL_ALERT",
                "SHP leakage current threshold exceeded",
                null,
                1,
                MeasurementWorkflowStep.ACQUIRE_SPECTRUM
        );
        try {
            mockMvc.perform(get("/wizard/automatic/run/status")
                            .with(user("operator")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(
                            "SHP leakage current threshold exceeded"
                    )))
                    .andExpect(content().string(containsString(
                            "data-workflow-action=\"acknowledge\""
                    )));

            mockMvc.perform(post("/wizard/automatic/run/acknowledge")
                            .with(user("operator"))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("acknowledged"));
        } finally {
            if (incidentService.snapshot().canAcknowledge()) {
                incidentService.acknowledge("test-cleanup");
            }
            if (incidentService.snapshot().canReset()) {
                incidentService.reset("test-cleanup");
            }
        }
    }

    @Test
    void rendersFullManualMeasurementWorkspace()
            throws Exception {
        mockMvc.perform(get("/wizard/manual"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MANUAL MEASUREMENTS")))
                .andExpect(content().string(containsString(
                        "Identification and mass"
                )))
                .andExpect(content().string(containsString(
                        "data-manual-action=\"scan\""
                )))
                .andExpect(content().string(containsString(
                        "data-manual-action=\"weigh\""
                )))
                .andExpect(content().string(containsString(
                        "data-manual-action=\"hatch/open\""
                )))
                .andExpect(content().string(containsString(
                        "data-manual-action=\"hv/enable\""
                )))
                .andExpect(content().string(containsString(
                        "data-manual-action=\"spectrum/start\""
                )));
    }

    @Test
    void delegatesManualMeasurementCommandsAndReturnsWorkspaceFragment()
            throws Exception {
        when(manualMeasurementService.stopSpectrumAcquisition()).thenReturn(true);

        mockMvc.perform(post("/wizard/manual/sample-code").with(csrf())
                        .param("sampleCode", "SAMPLE-17"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "id=\"wizard-manual-workspace\""
                )));
        mockMvc.perform(post("/wizard/manual/scan").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/weigh").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/hatch/open").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/hatch/close").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/hv/enable").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/hv/disable").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/spectrum/start").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/wizard/manual/spectrum/stop").with(csrf()))
                .andExpect(status().isOk());

        verify(manualMeasurementService).setSampleCode("SAMPLE-17");
        verify(manualMeasurementService).scanBarcode();
        verify(manualMeasurementService).readWeight();
        verify(manualMeasurementService).openHatch();
        verify(manualMeasurementService).closeHatch();
        verify(manualMeasurementService).enableHighVoltage();
        verify(manualMeasurementService).disableHighVoltage();
        verify(manualMeasurementService).startSpectrumAcquisition();
        verify(manualMeasurementService).stopSpectrumAcquisition();
    }

    @Test
    void rendersManualProtocolFailureInsideWorkspace() throws Exception {
        doThrow(new IllegalStateException("Spectrometer hatch is not confirmed closed"))
                .when(manualMeasurementService).closeHatch();

        mockMvc.perform(post("/wizard/manual/hatch/close").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Spectrometer hatch is not confirmed closed"
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

    private static ManualMeasurementSnapshot idleManualMeasurement() {
        return new ManualMeasurementSnapshot(
                null, ManualMeasurementState.IDLE, "", "",
                null, null, null, null, null, null, "", ""
        );
    }
}
