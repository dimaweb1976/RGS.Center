package com.rgs.center.controller;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.measurement.history.MeasurementHistoryCriteria;
import com.rgs.center.measurement.history.MeasurementHistoryEntry;
import com.rgs.center.measurement.history.MeasurementHistoryRepository;
import com.rgs.center.measurement.history.MeasurementMode;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import com.rgs.center.web.wizard.WizardMeasurementHistoryViewFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Search, detail and CSV export endpoints for the measurement database. */
@Controller
@RequestMapping("/wizard/database")
public class WizardMeasurementDatabaseController {

    private static final int DEFAULT_LIMIT = 25;
    private static final int EXPORT_LIMIT = 5000;

    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory homeViewFactory;
    private final MeasurementHistoryRepository historyRepository;
    private final WizardMeasurementHistoryViewFactory viewFactory;

    public WizardMeasurementDatabaseController(
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory homeViewFactory,
            MeasurementHistoryRepository historyRepository,
            WizardMeasurementHistoryViewFactory viewFactory
    ) {
        this.dashboardService = dashboardService;
        this.homeViewFactory = homeViewFactory;
        this.historyRepository = historyRepository;
        this.viewFactory = viewFactory;
    }

    @GetMapping
    public String page(
            @RequestParam(defaultValue = "") String sample,
            @RequestParam(defaultValue = "") String mode,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "25") int limit,
            Model model
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 250);
        MeasurementHistoryCriteria criteria = criteria(
                sample, mode, status, from, to, safeLimit
        );
        addHeader(model, "MEASUREMENT DATABASE");
        model.addAttribute(
                "historyView",
                viewFactory.page(
                        criteria,
                        text(from),
                        text(to),
                        historyRepository.find(criteria)
                )
        );
        return "wizard/measurement-database";
    }

    @GetMapping("/manual/{runId}")
    public String manual(@PathVariable UUID runId, Model model) {
        return detail(historyRepository.findManual(runId).orElseThrow(
                () -> new ResponseStatusException(NOT_FOUND, "Measurement not found")
        ), model);
    }

    @GetMapping("/automatic/{runId}/{objectNumber}")
    public String automatic(
            @PathVariable UUID runId,
            @PathVariable int objectNumber,
            Model model
    ) {
        return detail(historyRepository.findAutomatic(runId, objectNumber).orElseThrow(
                () -> new ResponseStatusException(NOT_FOUND, "Measurement object not found")
        ), model);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "") String sample,
            @RequestParam(defaultValue = "") String mode,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        List<MeasurementHistoryEntry> rows = historyRepository.find(criteria(
                sample, mode, status, from, to, EXPORT_LIMIT
        ));
        byte[] content = csv(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("rgscenter-measurements.csv", StandardCharsets.UTF_8)
                                .build().toString()
                )
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }

    private String detail(MeasurementHistoryEntry entry, Model model) {
        addHeader(model, "MEASUREMENT REPORT");
        model.addAttribute("measurement", viewFactory.detail(entry));
        return "wizard/measurement-report";
    }

    private void addHeader(Model model, String title) {
        model.addAttribute("wizardTitle", title);
        model.addAttribute(
                "wizardView",
                homeViewFactory.create(dashboardService.snapshot())
        );
    }

    private static MeasurementHistoryCriteria criteria(
            String sample,
            String mode,
            String status,
            LocalDate from,
            LocalDate to,
            int limit
    ) {
        ZoneId zone = ZoneId.systemDefault();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "From date must not be after To date"
            );
        }
        MeasurementMode selectedMode;
        try {
            selectedMode = mode == null || mode.isBlank()
                    ? null
                    : MeasurementMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown measurement mode");
        }
        return new MeasurementHistoryCriteria(
                sample,
                selectedMode,
                status,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
                limit
        );
    }

    private static String csv(List<MeasurementHistoryEntry> rows) {
        StringBuilder result = new StringBuilder("\uFEFF")
                .append("mode,run_id,object_number,sample_code,x,y,gross_mass_g,")
                .append("net_mass_g,measuring_time_min,volume_ml,geometry,tare_mass_g,")
                .append("started_at,finished_at,status,spectrum_reference,error\r\n");
        for (MeasurementHistoryEntry row : rows) {
            appendCsv(result,
                    row.mode().name(), row.runId(), row.objectNumber(), row.sampleCode(),
                    row.x(), row.y(), row.grossMassGrams(), row.netMassGrams(),
                    row.measuringTimeMinutes(), row.volumeMl(), row.geometry().name(),
                    row.tareMassGrams(), row.startedAt(), row.finishedAt(), row.status(),
                    row.spectrumReference(), row.error());
        }
        return result.toString();
    }

    private static void appendCsv(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) target.append(',');
            String value = values[index] == null ? "" : values[index].toString();
            if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
                value = "'" + value;
            }
            target.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        target.append("\r\n");
    }

    private static String text(LocalDate value) {
        return value == null ? "" : value.toString();
    }
}
