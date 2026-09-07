package com.rgs.center.gammapro;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Состояние процесса BOSON, определяемое через список трактов Gamma-PRO.
 *
 * <p>CONNECTED означает только то, что Gamma-PRO недавно вернула ожидаемый
 * тракт анализатора и канала. Проверка не читает состояние физического
 * USB-B-порта, фактическое высокое напряжение, исправность детектора или поток
 * импульсов.</p>
 */
public enum BosonConnectionStatus {
    CONNECTED,
    DISCONNECTED;

    private static final Pattern TRACT_PATTERN = Pattern.compile(
            "\\bTRACT\\d+\\s*\\(\\s*([^,()]+?)\\s*,\\s*([^,()]+?)\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Возвращает CONNECTED только для свежего списка с ожидаемым трактом.
     * Номер TRACT не фиксирован: важны значения анализатора и канала внутри
     * скобок, сравниваемые без учёта регистра.
     */
    public static BosonConnectionStatus from(
            List<String> tracts,
            String expectedMca,
            String expectedChannel,
            Instant tractsUpdatedAt,
            Duration staleAfter,
            Instant now
    ) {
        if (tractsUpdatedAt == null
                || tractsUpdatedAt.plus(staleAfter).isBefore(now)) {
            return DISCONNECTED;
        }
        for (String line : tracts) {
            Matcher matcher = TRACT_PATTERN.matcher(line);
            while (matcher.find()) {
                if (expectedMca.equalsIgnoreCase(matcher.group(1).trim())
                        && expectedChannel.equalsIgnoreCase(
                                matcher.group(2).trim()
                        )) {
                    return CONNECTED;
                }
            }
        }
        return DISCONNECTED;
    }
}
