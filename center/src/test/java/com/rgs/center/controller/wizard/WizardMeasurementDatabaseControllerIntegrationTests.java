package com.rgs.center.controller.wizard;

import com.rgs.center.measurement.manual.ManualMeasurementRepository;
import com.rgs.center.measurement.manual.ManualMeasurementResult;
import com.rgs.center.measurement.manual.ManualMeasurementState;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.SampleGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class WizardMeasurementDatabaseControllerIntegrationTests {

    private static final UUID ID = UUID.fromString(
            "c7e7b72e-fafe-4a6b-aef6-ad27a32eea57"
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ManualMeasurementRepository repository;
    @Autowired private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM automatic_measurements").update();
        jdbcClient.sql("DELETE FROM manual_measurements").update();
        Instant started = Instant.parse("2026-09-14T08:00:00Z");
        repository.insert(new ManualMeasurementResult(
                ID, "SAMPLE-STAGE-11", new BigDecimal("120.5"),
                new BigDecimal("95.5"),
                new MeasurementParameters(
                        30, 250, SampleGeometry.VIAL, 25, 0, started
                ),
                started, started.plusSeconds(1800),
                ManualMeasurementState.COMPLETED, "spectrum-stage-11", ""
        ));
    }

    @Test
    void rendersSearchableDatabaseAndMeasurementReport() throws Exception {
        mockMvc.perform(get("/wizard/database").param("sample", "STAGE-11"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Stored measurements")))
                .andExpect(content().string(containsString("SAMPLE-STAGE-11")))
                .andExpect(content().string(containsString("Open report")))
                .andExpect(content().string(containsString("stage 11")));

        mockMvc.perform(get("/wizard/database/manual/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Measurement parameters snapshot")))
                .andExpect(content().string(containsString("spectrum-stage-11")))
                .andExpect(content().string(containsString("Not applicable (manual mode)")));
    }

    @Test
    void exportsCurrentSearchAsUtf8Csv() throws Exception {
        mockMvc.perform(get("/wizard/database/export.csv")
                        .param("sample", "STAGE-11"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("rgscenter-measurements.csv")
                ))
                .andExpect(content().string(containsString("sample_code")))
                .andExpect(content().string(containsString("SAMPLE-STAGE-11")));
    }

    @Test
    void rejectsInvalidDateRangeAndUnknownRecord() throws Exception {
        mockMvc.perform(get("/wizard/database")
                        .param("from", "2026-09-15")
                        .param("to", "2026-09-14"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/wizard/database/manual/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
