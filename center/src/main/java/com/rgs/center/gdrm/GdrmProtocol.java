package com.rgs.center.gdrm;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Кодирование и разбор ASCII-протокола Gamma Dose Rate Meter.
 */
public final class GdrmProtocol {

    public static final String BROADCAST_ADDRESS = "X";

    private static final int MEASUREMENT_FIELD_COUNT = 17;
    private static final int OPERATING_FIELD_COUNT = 6;

    private GdrmProtocol() {
    }

    /**
     * Формирует команду с обязательным окончанием CR/LF.
     *
     * @param address однобуквенный адрес устройства
     * @param body тело команды без адреса и окончания строки
     * @return байты ASCII, готовые к отправке
     */
    public static byte[] command(String address, String body) {
        if (address == null || address.length() != 1) {
            throw new IllegalArgumentException("GDRM address must contain one character");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("GDRM command body must not be blank");
        }
        return (address + body + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Декодирует строку ответа без транспортного окончания CR/LF.
     *
     * @param frame принятый кадр
     * @return строка ASCII без окружающих пробелов
     */
    public static String decode(byte[] frame) {
        if (frame == null || frame.length == 0) {
            throw new PeripheralCommunicationException("Empty GDRM response");
        }
        return new String(frame, StandardCharsets.US_ASCII).trim();
    }

    /**
     * Разбирает ответ параметра вида {@code s:123456789}.
     *
     * @param frame принятый кадр
     * @param parameter ожидаемый символ параметра
     * @return значение после двоеточия
     */
    public static String parseParameter(byte[] frame, char parameter) {
        String response = decode(frame);
        if (response.length() < 3
                || response.charAt(0) != parameter
                || response.charAt(1) != ':') {
            throw new PeripheralCommunicationException(
                    "Unexpected GDRM parameter response: " + response
            );
        }
        return response.substring(2).trim();
    }

    /**
     * Разбирает 17 полей измерения формата 9.
     *
     * @param frame ответ команды {@code d}
     * @return типизированное измерение
     */
    public static GdrmMeasurement parseMeasurement(byte[] frame) {
        String response = decode(frame);
        String[] fields = response.split(";", -1);
        if (fields.length != MEASUREMENT_FIELD_COUNT) {
            throw new PeripheralCommunicationException(
                    "GDRM measurement must contain 17 fields, got " + fields.length
            );
        }

        List<GdrmChannelMeasurement> channels = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            int offset = index * 3;
            channels.add(new GdrmChannelMeasurement(
                    number(fields[offset]),
                    number(fields[offset + 1]),
                    number(fields[offset + 2])
            ));
        }

        return new GdrmMeasurement(
                channels,
                number(fields[12]),
                number(fields[13]),
                number(fields[14]),
                number(fields[15]),
                fields[16].trim(),
                Instant.now().getEpochSecond()
        );
    }

    /**
     * Разбирает шесть полей оперативного ответа команды {@code t}.
     *
     * @param frame принятый кадр
     * @return типизированные оперативные данные
     */
    public static GdrmOperatingData parseOperatingData(byte[] frame) {
        String response = decode(frame);
        String[] fields = response.split(";", -1);
        if (fields.length != OPERATING_FIELD_COUNT) {
            throw new PeripheralCommunicationException(
                    "GDRM operating response must contain 6 fields, got " + fields.length
            );
        }
        return new GdrmOperatingData(
                number(fields[0]),
                number(fields[1]),
                number(fields[2]),
                number(fields[3]),
                number(fields[4]),
                fields[5].trim(),
                Instant.now().getEpochSecond()
        );
    }

    /** Преобразует необязательное числовое поле протокола. */
    private static Double number(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw new PeripheralCommunicationException(
                    "Invalid GDRM numeric value: " + normalized,
                    exception
            );
        }
    }
}
