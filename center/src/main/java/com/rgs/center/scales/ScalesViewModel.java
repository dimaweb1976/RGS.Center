package com.rgs.center.scales;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Values prepared for rendering the live Thymeleaf fragment. */
public record ScalesViewModel(
        boolean connected,
        String connectionLabel,
        String connectionCssClass,
        boolean zero,
        String zeroLabel,
        String zeroCssClass,
        String stability,
        String weightGrams,
        String portName,
        String baudRate,
        String lastCheck,
        String lastMeasurement,
        String workerState,
        String lastError
) {
    private static final String EMPTY = "—";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static ScalesViewModel from(ScalesSnapshot snapshot) {
        ScalesReading reading = snapshot.reading();
        boolean zero = snapshot.connected()
                && reading != null
                && reading.isZeroGrams();
        long lastCheck = Math.max(
                snapshot.lastRequestEpochSeconds(),
                snapshot.lastResponseEpochSeconds()
        );
        String error = firstNonBlank(
                snapshot.lastError(),
                snapshot.communication() == null
                        ? null
                        : snapshot.communication().lastError()
        );
        return new ScalesViewModel(
                snapshot.connected(),
                snapshot.connected() ? "ONLINE" : "OFFLINE",
                snapshot.connected() ? "scales-online" : "scales-offline",
                zero,
                zero
                        ? "0 g"
                        : (snapshot.connected() ? "НЕ НОЛЬ" : "НЕТ ДАННЫХ"),
                zero ? "scales-zero" : "scales-not-zero",
                reading == null ? EMPTY : reading.stability().label(),
                reading == null
                        ? EMPTY
                        : reading.massGrams().stripTrailingZeros().toPlainString(),
                text(snapshot.portName()),
                snapshot.baudRate() > 0
                        ? Integer.toString(snapshot.baudRate())
                        : EMPTY,
                dateTime(lastCheck),
                reading == null
                        ? EMPTY
                        : dateTime(reading.receivedAtEpochSeconds()),
                snapshot.communication() == null
                        ? EMPTY
                        : snapshot.communication().state().name(),
                error
        );
    }

    private static String dateTime(long epochSeconds) {
        return epochSeconds <= 0
                ? EMPTY
                : DATE_TIME_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? EMPTY : value;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return EMPTY;
    }
}
