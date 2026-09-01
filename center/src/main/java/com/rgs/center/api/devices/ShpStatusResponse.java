package com.rgs.center.api.devices;

import com.rgs.center.shp.ShpReadinessStatus;

/** Latest complete SHP measurements and their evaluated technical status. */
public record ShpStatusResponse(
        Double ambientTemperatureCelsius,
        Double humidityPercent,
        Double atmosphericPressureHpa,
        Double leakageCurrent,
        Double chargeOutputVoltage,
        ShpReadinessStatus technicalStatus
) {
}
