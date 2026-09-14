package com.rgs.center.web.wizard;

import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.measurement.parameters.MeasurementParameters;

/** Данные рабочего поля подготовки автоматического измерения. */
public record WizardAutomaticView(
        String highVoltageRequest,
        String leakageCurrent,
        String leakageLimit,
        boolean leakageWithinLimit,
        MeasurementParameters parameters,
        CameraImagingSnapshot camera
) {
}
