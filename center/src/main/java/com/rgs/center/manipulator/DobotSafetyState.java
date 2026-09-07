package com.rgs.center.manipulator;

import java.util.ArrayList;
import java.util.List;

/** Побитовая интерпретация active-low SafetyState из байта 1420. */
public record DobotSafetyState(
        int rawValue,
        boolean emergencyStop,
        boolean protectiveStop,
        boolean reducedMode,
        boolean nonStop,
        boolean inMotion,
        boolean systemEmergencyStop,
        boolean userEmergencyStop,
        boolean outsideSafetyHome
) {
    /** @return декодированная active-low маска */
    public static DobotSafetyState decode(int rawValue) {
        int value = rawValue & 0xFF;
        return new DobotSafetyState(
                value,
                activeLow(value, 0),
                activeLow(value, 1),
                activeLow(value, 2),
                activeLow(value, 3),
                activeLow(value, 4),
                activeLow(value, 5),
                activeLow(value, 6),
                activeLow(value, 7)
        );
    }

    /** @return аварийные блокировки, при которых движение запрещено */
    public List<String> blockingFaults() {
        List<String> faults = new ArrayList<>();
        if (emergencyStop) faults.add("Emergency stop");
        if (protectiveStop) faults.add("Protective stop");
        if (systemEmergencyStop) faults.add("System emergency stop");
        if (userEmergencyStop) faults.add("User emergency stop");
        return List.copyOf(faults);
    }

    private static boolean activeLow(int value, int bit) {
        return (value & (1 << bit)) == 0;
    }
}
