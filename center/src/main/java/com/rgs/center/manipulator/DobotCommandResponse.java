package com.rgs.center.manipulator;

import java.util.OptionalLong;
import java.util.OptionalDouble;

/** Parsed Dashboard response: ErrorID,{values},EchoedCommand;. */
public record DobotCommandResponse(
        int errorId,
        String values,
        String echoedCommand,
        String raw
) {
    /** @return whether the controller accepted the command */
    public boolean successful() {
        return errorId == 0;
    }

    /** @return first numeric return value, normally a motion ResultID */
    public OptionalLong firstLongValue() {
        String first = values.split(",", 2)[0].trim();
        try {
            return first.isEmpty()
                    ? OptionalLong.empty()
                    : OptionalLong.of(Long.parseLong(first));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /** @return первое числовое значение ответа, включая дробное AI */
    public OptionalDouble firstDoubleValue() {
        String first = values.split(",", 2)[0].trim();
        try {
            return first.isEmpty()
                    ? OptionalDouble.empty()
                    : OptionalDouble.of(Double.parseDouble(first));
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }
}
