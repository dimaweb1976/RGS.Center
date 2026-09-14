package com.rgs.center.controller;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.measurement.parameters.SampleGeometry;
import com.rgs.center.web.wizard.MeasurementParametersForm;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Настройка параметров, общих для автоматического и ручного измерения. */
@Controller
@RequestMapping("/wizard/measurement-parameters")
public class WizardMeasurementParametersController {

    private final DashboardSnapshotService dashboardService;
    private final WizardHomeViewFactory homeViewFactory;
    private final MeasurementParametersService parametersService;

    public WizardMeasurementParametersController(
            DashboardSnapshotService dashboardService,
            WizardHomeViewFactory homeViewFactory,
            MeasurementParametersService parametersService
    ) {
        this.dashboardService = dashboardService;
        this.homeViewFactory = homeViewFactory;
        this.parametersService = parametersService;
    }

    /** @return полная страница с последней сохранённой версией параметров */
    @GetMapping
    public String page(Model model) {
        addCommonView(model);
        addForm(model, MeasurementParametersForm.from(parametersService.current()));
        return "wizard/measurement-parameters";
    }

    /** Проверяет и атомарно сохраняет значения формы. */
    @PostMapping
    public String save(
            @RequestParam String measuringTimeMinutes,
            @RequestParam String volumeMl,
            @RequestParam String geometry,
            @RequestParam String tareMassGrams,
            @RequestParam String version,
            Model model
    ) {
        MeasurementParametersForm submitted = new MeasurementParametersForm(
                measuringTimeMinutes,
                volumeMl,
                geometry,
                tareMassGrams,
                version
        );
        try {
            MeasurementParameters saved = parametersService.save(
                    parseLong(measuringTimeMinutes, "Measuring time"),
                    parseDouble(volumeMl, "Volume"),
                    parseGeometry(geometry),
                    parseDouble(tareMassGrams, "Tare mass"),
                    parseLong(version, "Version")
            );
            addForm(model, MeasurementParametersForm.from(saved));
            model.addAttribute("parametersMessage", "Measurement parameters saved");
        } catch (RuntimeException exception) {
            addForm(model, submitted);
            model.addAttribute("parametersError", errorMessage(exception));
        }
        return "wizard/fragments/measurement-parameters-form :: formPanel";
    }

    private void addCommonView(Model model) {
        model.addAttribute("wizardTitle", "MEASUREMENT PARAMETERS");
        model.addAttribute(
                "wizardView",
                homeViewFactory.create(dashboardService.snapshot())
        );
    }

    private static void addForm(Model model, MeasurementParametersForm form) {
        model.addAttribute("parametersForm", form);
        model.addAttribute("sampleGeometries", SampleGeometry.values());
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be an integer", exception);
        }
    }

    private static double parseDouble(String value, String label) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + " must be a number", exception);
        }
    }

    private static SampleGeometry parseGeometry(String value) {
        try {
            return SampleGeometry.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported sample geometry", exception);
        }
    }

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
