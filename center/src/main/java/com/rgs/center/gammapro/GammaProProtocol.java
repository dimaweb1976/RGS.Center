package com.rgs.center.gammapro;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Objects;

/** Формирование команд и классификация строк протокола Gamma-PRO, глава 17. */
public final class GammaProProtocol {

    public static final String GET_TRACTS_NAME = "GET_TRACTS_NAME";
    public static final String START = "START";
    public static final String STOP = "STOP";
    public static final String BREAK = "BREAK";
    public static final String WAIT_MEAS_END = "WAIT_MEAS_END";
    public static final String SET_HV = "SET_HV";

    private static final String LINE_ENDING = "\n\r";

    private GammaProProtocol() {
    }

    /** @return команда выбора текущего спектрометрического тракта */
    public static String setTract(String mcaName, String channelName) {
        requireParameter(mcaName, "MCA name");
        requireParameter(channelName, "Channel name");
        return "SET_TRACT(" + mcaName + "," + channelName + ")";
    }

    /** @return команда задания экспозиции измерения */
    public static String setPreset(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    "Measurement preset must be positive"
            );
        }
        return "SET_PRESET(" + seconds + ")";
    }

    /**
     * Формирует документированную команду управления высоким напряжением
     * текущего тракта Gamma-PRO.
     *
     * @param enabled {@code true} для включения, {@code false} для выключения
     * @param value целевое целочисленное значение напряжения
     * @param valueType физический смысл передаваемого значения
     * @return команда {@code SET_HV(IsOn,Value_HV,Type_value)}
     */
    public static String setHighVoltage(
            boolean enabled,
            int value,
            GammaProHighVoltageValueType valueType
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "High-voltage value must not be negative"
            );
        }
        Objects.requireNonNull(valueType, "HV value type must not be null");
        return SET_HV + "(" + (enabled ? 1 : 0) + "," + value + ","
                + valueType.protocolCode() + ")";
    }

    /** Добавляет документированный конец строки LF CR и кодирует команду. */
    public static byte[] encode(String command, Charset charset) {
        Objects.requireNonNull(charset, "Charset must not be null");
        String value = Objects.requireNonNull(
                command,
                "Command must not be null"
        ).trim();
        if (value.isEmpty() || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid Gamma-PRO command");
        }
        return (value + LINE_ENDING).getBytes(charset);
    }

    /** Выделяет имя команды до первой открывающей скобки. */
    public static String commandName(String command) {
        String value = command.trim();
        int parameters = value.indexOf('(');
        return (parameters < 0 ? value : value.substring(0, parameters))
                .toUpperCase(Locale.ROOT);
    }

    /** Разбирает одну строку ответа сервера. */
    public static ResponseLine parseResponse(String line) {
        String value = Objects.requireNonNull(line).trim();
        int dataSeparator = value.indexOf('>');
        int stateSeparator = value.indexOf('=');
        if (dataSeparator > 0
                && (stateSeparator < 0 || dataSeparator < stateSeparator)) {
            return new ResponseLine(
                    value.substring(0, dataSeparator).trim()
                            .toUpperCase(Locale.ROOT),
                    ResponseType.DATA,
                    value.substring(dataSeparator + 1).trim(),
                    value
            );
        }
        if (stateSeparator > 0) {
            String state = value.substring(stateSeparator + 1).trim()
                    .toUpperCase(Locale.ROOT);
            ResponseType type = switch (state) {
                case "OK" -> ResponseType.OK;
                case "DO" -> ResponseType.DO;
                case "ER" -> ResponseType.ERROR;
                default -> ResponseType.UNKNOWN;
            };
            return new ResponseLine(
                    value.substring(0, stateSeparator).trim()
                            .toUpperCase(Locale.ROOT),
                    type,
                    state,
                    value
            );
        }
        return new ResponseLine("", ResponseType.UNKNOWN, value, value);
    }

    static void requireParameter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.indexOf(',') >= 0 || value.indexOf('(') >= 0
                || value.indexOf(')') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    name + " contains a protocol separator"
            );
        }
    }

    /** Тип строки ответа Gamma-PRO. */
    public enum ResponseType {
        DATA,
        DO,
        OK,
        ERROR,
        UNKNOWN
    }

    /**
     * @param commandName имя команды из ответа
     * @param type тип строки
     * @param payload данные после {@code >} или {@code =}
     * @param raw исходная нормализованная строка
     */
    public record ResponseLine(
            String commandName,
            ResponseType type,
            String payload,
            String raw
    ) {
    }
}
