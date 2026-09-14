package com.rgs.center.web.wizard;

import com.rgs.center.measurement.parameters.MeasurementParameters;

/** Строковые значения формы, сохраняемые при ошибке преобразования. */
public record MeasurementParametersForm(
        String measuringTimeMinutes,
        String volumeMl,
        String geometry,
        String tareMassGrams,
        String version
) {
    public static MeasurementParametersForm from(MeasurementParameters value) {
        return new MeasurementParametersForm(
                Long.toString(value.measuringTimeMinutes()),
                Double.toString(value.volumeMl()),
                value.geometry().name(),
                Double.toString(value.tareMassGrams()),
                Long.toString(value.version())
        );
    }
}
