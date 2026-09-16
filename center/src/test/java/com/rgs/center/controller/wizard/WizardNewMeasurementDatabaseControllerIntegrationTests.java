package com.rgs.center.controller.wizard;

import com.rgs.center.measurement.manual.ManualMeasurementRepository;
import com.rgs.center.measurement.manual.ManualMeasurementResult;
import com.rgs.center.measurement.manual.ManualMeasurementState;
import com.rgs.center.measurement.history.MeasurementHistoryRepository;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.measurement.workflow.MeasurementObjectSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.MeasurementWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class WizardNewMeasurementDatabaseControllerIntegrationTests {

    private static final UUID RUN_ID = UUID.fromString(
            "e7ee466e-b9d1-455b-b756-f160818a2f49"
    );
    private static final UUID AUTO_RUN_ID = UUID.fromString(
            "2250259e-17ea-488f-8d71-ea98eef735bb"
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ManualMeasurementRepository repository;
    @Autowired private MeasurementHistoryRepository historyRepository;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM automatic_measurements").update();
        jdbcClient.sql("DELETE FROM manual_measurements").update();
        Instant started = Instant.parse("2026-09-14T08:00:00Z");
        repository.insert(new ManualMeasurementResult(
                RUN_ID, "SAMPLE-WIZARD-NEW", new BigDecimal("120.5"),
                new BigDecimal("95.5"),
                new MeasurementParameters(
                        30, 250, SampleGeometry.VIAL, 25, 0, started
                ),
                started, started.plusSeconds(1800),
                ManualMeasurementState.COMPLETED, "spectrum-wizard-new", ""
        ));
        historyRepository.insertAutomaticRun(new MeasurementWorkflowSnapshot(
                AUTO_RUN_ID, MeasurementWorkflowState.COMPLETED, null, null,
                started.plusSeconds(7200), started.plusSeconds(10800),
                new MeasurementParameters(
                        60, 300, SampleGeometry.BOTTLE, 25, 0, started
                ),
                "", List.of(new MeasurementObjectSnapshot(
                        3, 120.5, 44.25, "COMPLETED", null, "AUTO-WIZARD-NEW",
                        new BigDecimal("130.0"), new BigDecimal("105.0"),
                        "auto-spectrum-wizard-new", ""
                )), List.of()
        ));
    }

    @Test
    void showsActualHistoryInNewDesignAndOwnReport() throws Exception {
        mockMvc.perform(get("/wizard-new/database").param("sample", "SAMPLE-WIZARD-NEW"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SAMPLE-WIZARD-NEW")))
                .andExpect(content().string(containsString("Recorded runs")))
                .andExpect(content().string(containsString("Identifiers, not files")))
                .andExpect(content().string(containsString(
                        "/wizard-new/database/manual/" + RUN_ID
                )))
                .andExpect(content().string(not(containsString("BATCH-2026-0418"))))
                .andExpect(content().string(containsString("/wizard-new/css/database.css")));

        mockMvc.perform(get("/wizard-new/database/manual/{runId}", RUN_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Measurement parameters snapshot")))
                .andExpect(content().string(containsString("spectrum-wizard-new")))
                .andExpect(content().string(containsString("Not applicable (manual mode)")));
    }

    @Test
    void filtersEmptyResultsAndRejectsInvalidSearch() throws Exception {
        mockMvc.perform(get("/wizard-new/database").param("sample", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "No measurements match the selected filters"
                )));
        mockMvc.perform(get("/wizard-new/database")
                        .param("from", "2026-09-15")
                        .param("to", "2026-09-14"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/wizard-new/database").param("mode", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/wizard-new/database/manual/{runId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void linksAutomaticObjectToNewReport() throws Exception {
        mockMvc.perform(get("/wizard-new/database")
                        .param("sample", "AUTO-WIZARD-NEW")
                        .param("mode", "AUTOMATIC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AUTO-WIZARD-NEW")))
                .andExpect(content().string(containsString(
                        "/wizard-new/database/automatic/" + AUTO_RUN_ID + "/3"
                )))
                .andExpect(content().string(not(containsString("SAMPLE-WIZARD-NEW"))));

        mockMvc.perform(get("/wizard-new/database/automatic/{runId}/{number}",
                        AUTO_RUN_ID, 3))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("auto-spectrum-wizard-new")))
                .andExpect(content().string(containsString("X 120.50, Y 44.25")));
    }

    @Test
    void selectsPersistedRunWithoutInventingBatchesOrDownloads() throws Exception {
        mockMvc.perform(get("/wizard-new/database").param("runId", RUN_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Recorded runs")))
                .andExpect(content().string(containsString("SAMPLE-WIZARD-NEW")))
                .andExpect(content().string(containsString(AUTO_RUN_ID.toString())))
                .andExpect(content().string(containsString("Report download")))
                .andExpect(content().string(containsString("Unavailable")))
                .andExpect(content().string(not(containsString("Download all"))));
    }
}
