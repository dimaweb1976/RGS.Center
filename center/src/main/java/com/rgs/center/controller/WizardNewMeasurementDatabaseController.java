package com.rgs.center.controller;

import com.rgs.center.measurement.history.MeasurementHistoryCriteria;
import com.rgs.center.measurement.history.MeasurementHistoryEntry;
import com.rgs.center.measurement.history.MeasurementHistoryRepository;
import com.rgs.center.measurement.history.MeasurementMode;
import com.rgs.center.web.wizard.WizardMeasurementHistoryViewFactory;
import com.rgs.center.web.wizardnew.WizardNewShellModelService;
import com.rgs.center.web.wizardnew.WizardNewHistoryViewFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** New visual pages backed by the same measurement history as the current Wizard. */
@Controller
@RequestMapping("/wizard-new/database")
public class WizardNewMeasurementDatabaseController {

    private final MeasurementHistoryRepository historyRepository;
    private final WizardMeasurementHistoryViewFactory viewFactory;
    private final WizardNewShellModelService shellModelService;
    private final WizardNewHistoryViewFactory historyViewFactory;

    public WizardNewMeasurementDatabaseController(
            MeasurementHistoryRepository historyRepository,
            WizardMeasurementHistoryViewFactory viewFactory,
            WizardNewShellModelService shellModelService,
            WizardNewHistoryViewFactory historyViewFactory
    ) {
        this.historyRepository = historyRepository;
        this.viewFactory = viewFactory;
        this.shellModelService = shellModelService;
        this.historyViewFactory = historyViewFactory;
    }

    @GetMapping({"", "/"})
    public String page(
            @RequestParam(defaultValue = "") String sample,
            @RequestParam(defaultValue = "") String mode,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(required = false) UUID runId,
            Model model
    ) {
        MeasurementHistoryCriteria criteria = criteria(sample, mode, status, from, to, limit);
        shellModelService.addTo(model, "Measurement database", "database");
        var entries = historyRepository.find(criteria);
        var historyView = viewFactory.page(
                criteria,
                from == null ? "" : from.toString(),
                to == null ? "" : to.toString(),
                entries
        );
        model.addAttribute("historyView", historyView);
        model.addAttribute("runView", historyViewFactory.create(entries, historyView, runId));
        return "wizard-new/measurement-database";
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

    private String detail(MeasurementHistoryEntry entry, Model model) {
        shellModelService.addTo(model, "Measurement report", "database");
        model.addAttribute("measurement", viewFactory.detail(entry));
        return "wizard-new/measurement-report";
    }

    private static MeasurementHistoryCriteria criteria(
            String sample,
            String mode,
            String status,
            LocalDate from,
            LocalDate to,
            int limit
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "From date must not be after To date");
        }
        MeasurementMode selectedMode;
        try {
            selectedMode = mode == null || mode.isBlank()
                    ? null : MeasurementMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown measurement mode");
        }
        ZoneId zone = ZoneId.systemDefault();
        return new MeasurementHistoryCriteria(
                sample,
                selectedMode,
                status,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
                Math.min(Math.max(limit, 1), 250)
        );
    }
}
