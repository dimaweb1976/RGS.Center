package com.rgs.center.nicole;

import java.util.ArrayList;
import java.util.List;

/** Расшифровывает битовую маску предупреждений PONG. */
public final class NicoleWarningDecoder {

    private static final String[] WARNING_NAMES = {
            "No pressure",
            "Overpressure",
            "LN2 very high level",
            "Hot end overheating",
            "Motor overheating",
            null,
            null,
            null,
            "Working pressure exceeded by 20%",
            "LN2 very low level",
            "LN2 low level",
            "LN2 high level",
            "Cooling",
            "Hot end 45 °C exceeded",
            "Motor 45 °C exceeded",
            "Working pressure exceeded by 10%"
    };

    private NicoleWarningDecoder() {
    }

    /** @return список активных предупреждений в порядке битов */
    public static List<String> decode(int flags) {
        List<String> values = new ArrayList<>();
        for (int bit = 0; bit < WARNING_NAMES.length; bit++) {
            if ((flags & (1 << bit)) != 0) {
                String name = WARNING_NAMES[bit];
                values.add(name == null ? "Bit " + bit : name);
            }
        }
        return List.copyOf(values);
    }

    /** @return строка для таблицы либо {@code None} при нулевой маске */
    public static String describe(int flags) {
        List<String> values = decode(flags);
        return values.isEmpty() ? "None" : String.join(", ", values);
    }
}
