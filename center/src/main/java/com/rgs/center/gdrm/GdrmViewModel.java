package com.rgs.center.gdrm;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Подготовленные для HTML значения текущего состояния GDRM. */
public record GdrmViewModel(
        boolean connected,
        String connectionStatus,
        String portName,
        String baudRate,
        String serialNumber,
        String firmwareVersion,
        String deviceStatus,
        String averageDoseRate,
        String averageStatisticalError,
        String accumulatedDose,
        String measurementTime,
        String lastUpdate,
        String operatingVoltage,
        String highVoltage,
        String temperature,
        String humidity,
        String pressure,
        String workerState,
        String lastError,
        List<GdrmChannelView> channels
) {
    private static final String EMPTY = "—";
    private static final double SIEVERT_TO_NANOSIEVERT = 1_000_000_000.0;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Преобразует внутренний снимок сервиса в безопасные и удобные для человека строки.
     *
     * @param snapshot последний снимок фонового GDRM-сервиса
     * @return модель HTML-представления
     */
    public static GdrmViewModel from(GdrmSnapshot snapshot) {
        GdrmMeasurement measurement = snapshot.measurement();
        GdrmOperatingData operatingData = snapshot.operatingData();
        return new GdrmViewModel(
                snapshot.connected(),
                snapshot.connected() ? "CONNECTED" : "OFFLINE",
                text(snapshot.portName()),
                snapshot.baudRate() > 0 ? Integer.toString(snapshot.baudRate()) : EMPTY,
                text(snapshot.serialNumber()),
                text(snapshot.firmwareVersion()),
                measurement == null ? EMPTY : text(measurement.deviceStatus()),
                measurement == null ? EMPTY : nanosieverts(measurement.weightedDoseRateSvPerHour()),
                measurement == null ? EMPTY : percent(measurement.weightedRelativeStatisticalError()),
                measurement == null ? EMPTY : nanosieverts(measurement.accumulatedDoseSv()),
                measurement == null ? EMPTY : duration(measurement.accumulationTimeSeconds()),
                measurement == null ? EMPTY : dateTime(measurement.receivedAtEpochSeconds()),
                operatingData == null ? EMPTY : number(operatingData.operatingVoltageVolts()),
                operatingData == null ? EMPTY : number(operatingData.highVoltageVolts()),
                operatingData == null ? EMPTY : number(operatingData.temperatureCelsius()),
                operatingData == null ? EMPTY : number(operatingData.relativeHumidityPercent()),
                operatingData == null ? EMPTY : number(operatingData.airPressureHpa()),
                snapshot.communication() == null ? EMPTY : snapshot.communication().state().name(),
                firstNonBlank(snapshot.lastError(),
                        snapshot.communication() == null ? null : snapshot.communication().lastError()),
                channelViews(measurement)
        );
    }

    /** Формирует строки таблицы для возвращённых прибором каналов. */
    private static List<GdrmChannelView> channelViews(GdrmMeasurement measurement) {
        if (measurement == null) {
            return List.of();
        }
        List<GdrmChannelView> result = new ArrayList<>();
        for (int index = 0; index < measurement.channels().size(); index++) {
            GdrmChannelMeasurement channel = measurement.channels().get(index);
            result.add(new GdrmChannelView(
                    index + 1,
                    number(channel.countsPerSecond()),
                    nanosieverts(channel.doseRateSvPerHour()),
                    percent(channel.relativeStatisticalError())
            ));
        }
        return List.copyOf(result);
    }

    /** Переводит величину из Зв в нЗв и форматирует без экспоненты. */
    private static String nanosieverts(Double value) {
        return value == null ? EMPTY : decimal().format(value * SIEVERT_TO_NANOSIEVERT);
    }

    /** Переводит долю в проценты. */
    private static String percent(Double value) {
        return value == null ? EMPTY : decimal().format(value * 100.0);
    }

    /** Форматирует обычное числовое значение. */
    private static String number(Double value) {
        return value == null ? EMPTY : decimal().format(value);
    }

    /** Форматирует время измерения без ограничения в 24 часа. */
    private static String duration(Double seconds) {
        if (seconds == null) {
            return EMPTY;
        }
        Duration duration = Duration.ofSeconds(Math.max(0L, Math.round(seconds)));
        return "%02d:%02d:%02d".formatted(
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    /** Форматирует epoch time последнего измерения в часовом поясе сервера. */
    private static String dateTime(long epochSeconds) {
        return epochSeconds <= 0 ? EMPTY : DATE_TIME_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    /** Возвращает строку либо маркер отсутствующего значения. */
    private static String text(String value) {
        return value == null || value.isBlank() ? EMPTY : value;
    }

    /** Выбирает первую непустую ошибку либо маркер отсутствия ошибки. */
    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return EMPTY;
    }

    /** Создаёт независимый форматтер, поскольку DecimalFormat не потокобезопасен. */
    private static DecimalFormat decimal() {
        return new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));
    }
}
