package com.rgs.center.web.wizard;

import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.dashboard.model.BosonEquipmentSnapshot;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.ShpEquipmentSnapshot;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Собирает read-only показатели экрана подготовки из общей витрины. */
@Component
public class WizardAutomaticViewFactory {

    private final SettingsService settingsService;
    private final MeasurementParametersService parametersService;

    public WizardAutomaticViewFactory(
            SettingsService settingsService,
            MeasurementParametersService parametersService
    ) {
        this.settingsService = settingsService;
        this.parametersService = parametersService;
    }

    /**
     * @param snapshot согласованный снимок оборудования
     * @param camera текущее состояние снимка и распознавания
     * @return модель этапа подготовки без предположения о фактически поданном HV
     */
    public WizardAutomaticView create(
            DashboardSnapshot snapshot,
            CameraImagingSnapshot camera
    ) {
        BosonEquipmentSnapshot boson = snapshot.equipment().boson();
        ShpEquipmentSnapshot shp = snapshot.equipment().shp();
        double limit = settingsService.getValue(
                SettingsService.SHP_READY_MAX_LEAKAGE_CURRENT,
                Double.class
        );
        Double current = shp.leakageCurrent();
        return new WizardAutomaticView(
                highVoltageRequest(boson),
                decimal(current, " µA"),
                decimal(limit, " µA"),
                current != null && Double.isFinite(current) && current < limit,
                parametersService.current(),
                camera
        );
    }

    private static String highVoltageRequest(BosonEquipmentSnapshot boson) {
        if (boson.highVoltageRequested() == null) {
            return "No data";
        }
        if (!boson.highVoltageRequested()) {
            return "Off requested";
        }
        return boson.requestedHighVoltage() == null
                ? "On requested"
                : boson.requestedHighVoltage() + " V requested";
    }

    private static String decimal(Double value, String suffix) {
        return value == null || !Double.isFinite(value)
                ? "No data"
                : String.format(Locale.ROOT, "%.3f%s", value, suffix);
    }

    private static String decimal(double value, String suffix) {
        return String.format(Locale.ROOT, "%.3f%s", value, suffix);
    }
}
