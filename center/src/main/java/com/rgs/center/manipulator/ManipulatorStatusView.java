package com.rgs.center.manipulator;

import java.util.List;

/** Результат вычисления связи и технической готовности манипулятора. */
public record ManipulatorStatusView(
        ManipulatorConnectionStatus connectionStatus,
        ManipulatorReadinessStatus readiness,
        SpectrometerHatchStatus hatchStatus,
        List<String> reasons
) {
    public ManipulatorStatusView {
        reasons = List.copyOf(reasons);
    }
}
