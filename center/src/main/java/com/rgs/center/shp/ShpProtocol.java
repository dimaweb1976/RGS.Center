package com.rgs.center.shp;

import com.rgs.center.peripheral.PeripheralCommunicationException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Кодирование и проверка бинарного протокола SHP версии 2.4. */
public final class ShpProtocol {

    public static final byte START = (byte) 0xC0;
    public static final byte STOP = 0x0C;
    public static final byte ESCAPE = (byte) 0xAA;
    public static final byte SENSORS_COMMAND = 0x71;
    public static final byte SENSORS_ACK = (byte) 0x91;
    public static final byte SETUP_COMMAND = 0x75;
    public static final byte SETUP_ACK = (byte) 0x95;

    private static final int COMMAND_BODY_LENGTH = 7;
    private static final int ANSWER_BODY_LENGTH = 17;

    private ShpProtocol() {
    }

    /**
     * Формирует команду чтения одного датчика SENSORS_SHP.
     *
     * @param parameter номер параметра 1..9
     * @return транспортный кадр с CRC и byte stuffing
     */
    public static byte[] readSensorCommand(int parameter) {
        requireRange(parameter, 1, 9, "SHP sensor parameter");
        return encode(new byte[]{
                SENSORS_COMMAND,
                (byte) parameter,
                0, 0, 0, 0, 0
        });
    }

    /** @return команда чтения значения резистора обратной связи */
    public static byte[] readFeedbackResistanceCommand() {
        return encode(new byte[]{SETUP_COMMAND, 7, 0, 0, 0, 0, 0});
    }

    /**
     * Разбирает числовой ответ SENSORS_SHP_ACK с float IEEE 754 big-endian.
     *
     * @param frame полный транспортный кадр
     * @param expectedParameter ожидаемый номер датчика
     * @return значение параметра
     */
    public static double parseSensorValue(
            byte[] frame,
            int expectedParameter
    ) {
        byte[] body = decode(frame);
        requireAnswer(body, SENSORS_ACK, expectedParameter);
        return ByteBuffer.wrap(body, 2, Float.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getFloat();
    }

    /**
     * Разбирает ответ чтения резистора обратной связи SETUP_SHP_ACK.
     *
     * @param frame полный транспортный кадр
     * @return значение резистора из D3
     */
    public static int parseFeedbackResistance(byte[] frame) {
        byte[] body = decode(frame);
        requireAnswer(body, SETUP_ACK, 7);
        if ((body[2] & 0xFF) != 0) {
            throw new PeripheralCommunicationException(
                    "SHP setup response is not a read acknowledgement"
            );
        }
        return body[3] & 0xFF;
    }

    /** Упаковывает семибайтовое тело команды согласно протоколу SHP. */
    static byte[] encode(byte[] body) {
        if (body == null || body.length != COMMAND_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "SHP command body must contain 7 bytes"
            );
        }
        return encodePayload(body);
    }

    /** Формирует корректный ответный кадр для эмуляторов и модульных тестов. */
    static byte[] encodeAnswer(byte[] body) {
        if (body == null || body.length != ANSWER_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "SHP answer body must contain 17 bytes"
            );
        }
        return encodePayload(body);
    }

    /** Упаковывает тело произвольной допустимой длины с CRC и stuffing. */
    private static byte[] encodePayload(byte[] body) {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(16);
        frame.write(START);
        for (byte value : body) {
            writeStuffed(frame, value);
        }
        writeStuffed(frame, crc8(body));
        frame.write(STOP);
        return frame.toByteArray();
    }

    /** Снимает byte stuffing, проверяет длину и CRC, возвращая 17 байт ответа. */
    static byte[] decode(byte[] frame) {
        if (frame == null || frame.length < 3
                || frame[0] != START
                || frame[frame.length - 1] != STOP) {
            throw new PeripheralCommunicationException(
                    "Invalid SHP frame boundaries"
            );
        }
        ByteArrayOutputStream unpacked = new ByteArrayOutputStream(18);
        for (int index = 1; index < frame.length - 1; index++) {
            byte value = frame[index];
            if (value != ESCAPE) {
                unpacked.write(value);
                continue;
            }
            if (++index >= frame.length - 1) {
                throw new PeripheralCommunicationException(
                        "Incomplete SHP escape sequence"
                );
            }
            unpacked.write(unescape(frame[index]));
        }

        byte[] payload = unpacked.toByteArray();
        if (payload.length != ANSWER_BODY_LENGTH + 1) {
            throw new PeripheralCommunicationException(
                    "SHP answer must contain 17 body bytes and CRC, got "
                            + payload.length
            );
        }
        byte[] body = Arrays.copyOf(payload, ANSWER_BODY_LENGTH);
        byte receivedCrc = payload[ANSWER_BODY_LENGTH];
        byte calculatedCrc = crc8(body);
        if (receivedCrc != calculatedCrc) {
            throw new PeripheralCommunicationException(
                    "SHP CRC mismatch: expected=0x%02X, received=0x%02X"
                            .formatted(calculatedCrc & 0xFF, receivedCrc & 0xFF)
            );
        }
        return body;
    }

    /** Вычисляет CRC-8 с отражённым полиномом 0x8C и начальным значением 0. */
    static byte crc8(byte[] values) {
        int crc = 0;
        for (byte value : values) {
            int current = value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                int feedback = (current ^ crc) & 1;
                crc >>>= 1;
                current >>>= 1;
                if (feedback != 0) {
                    crc ^= 0x8C;
                }
            }
        }
        return (byte) crc;
    }

    /** Экранирует зарезервированные транспортные байты. */
    private static void writeStuffed(ByteArrayOutputStream output, byte value) {
        if (value == START) {
            output.write(ESCAPE);
            output.write(0x3F);
        } else if (value == STOP) {
            output.write(ESCAPE);
            output.write(0xF3);
        } else if (value == ESCAPE) {
            output.write(ESCAPE);
            output.write(0x55);
        } else {
            output.write(value);
        }
    }

    /** Восстанавливает зарезервированный байт из escape-последовательности. */
    private static int unescape(byte value) {
        return switch (value & 0xFF) {
            case 0x3F -> START & 0xFF;
            case 0xF3 -> STOP & 0xFF;
            case 0x55 -> ESCAPE & 0xFF;
            default -> throw new PeripheralCommunicationException(
                    "Unknown SHP escape byte 0x%02X".formatted(value & 0xFF)
            );
        };
    }

    /** Проверяет код ответа и номер запрошенного параметра. */
    private static void requireAnswer(
            byte[] body,
            byte expectedAnswer,
            int expectedParameter
    ) {
        if (body[0] != expectedAnswer) {
            throw new PeripheralCommunicationException(
                    "Unexpected SHP answer 0x%02X".formatted(body[0] & 0xFF)
            );
        }
        if ((body[1] & 0xFF) != expectedParameter) {
            throw new PeripheralCommunicationException(
                    "Unexpected SHP parameter " + (body[1] & 0xFF)
                            + ", expected " + expectedParameter
            );
        }
    }

    /** Проверяет числовой диапазон параметра команды. */
    private static void requireRange(
            int value,
            int minimum,
            int maximum,
            String name
    ) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum
            );
        }
    }
}
