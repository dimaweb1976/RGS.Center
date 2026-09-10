package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Нормализованная телеметрия двух TCP-каналов манипулятора Dobot. */
public record ManipulatorEquipmentSnapshot(
        DeviceHealth health,
        String host,
        int commandPort,
        int feedbackPort,
        Pose pose,
        Motion motion,
        Io io,
        Hatch hatch,
        Safety safety,
        Telemetry telemetry
) {
    public ManipulatorEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        host = host == null ? "" : host;
        validatePort(commandPort, "commandPort");
        validatePort(feedbackPort, "feedbackPort");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(io, "io");
        Objects.requireNonNull(hatch, "hatch");
        Objects.requireNonNull(safety, "safety");
        Objects.requireNonNull(telemetry, "telemetry");
    }

    private static void validatePort(int port, String name) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException(name + " is outside TCP range");
        }
    }

    /** Декартова позиция и ориентация TCP или пустые значения до feedback. */
    public record Pose(
            Double x,
            Double y,
            Double z,
            Double rx,
            Double ry,
            Double rz
    ) {
    }

    /** Состояние движения и выполнения Dashboard-команды. */
    public record Motion(
            Long robotMode,
            Boolean enabled,
            Boolean running,
            Boolean queuedCommandRunning,
            Boolean error,
            Long currentCommandId
    ) {
    }

    /** Полные цифровые маски без потери разрядов. */
    public record Io(String digitalInputsHex, String digitalOutputsHex) {
        public Io {
            digitalInputsHex = normalize(digitalInputsHex);
            digitalOutputsHex = normalize(digitalOutputsHex);
        }
    }

    /** Концевики и независимое аналоговое наблюдение положения люка. */
    public record Hatch(
            HatchState state,
            Integer closedInputBit,
            Boolean closedLimit,
            Integer openInputBit,
            Boolean openLimit,
            boolean analogEnabled,
            Integer analogInputIndex,
            Double analogValue,
            Instant analogReceivedAt,
            String analogLastError
    ) {
        public Hatch {
            Objects.requireNonNull(state, "state");
            analogLastError = normalize(analogLastError);
        }
    }

    /** Нормализованное положение люка по дискретным концевикам. */
    public enum HatchState {
        CLOSED,
        OPEN,
        INTERMEDIATE,
        INVALID,
        UNKNOWN
    }

    /** Декодированные сигналы безопасности Dobot. */
    public record Safety(
            String rawHex,
            Boolean emergencyStop,
            Boolean protectiveStop,
            Boolean reducedMode,
            Boolean nonStop,
            Boolean inMotion,
            Boolean systemEmergencyStop,
            Boolean userEmergencyStop,
            Boolean outsideSafetyHome,
            List<String> blockingFaults
    ) {
        public Safety {
            rawHex = normalize(rawHex);
            blockingFaults = List.copyOf(Objects.requireNonNull(
                    blockingFaults,
                    "blockingFaults"
            ));
        }
    }

    /** Диагностика потоков и актуальности последнего feedback-кадра. */
    public record Telemetry(
            String commandChannelState,
            String feedbackChannelState,
            Long controllerTimestampMillis,
            Instant feedbackReceivedAt,
            Long feedbackAgeMillis,
            long bytesReceived,
            long validFrames,
            long droppedFrames
    ) {
        public Telemetry {
            commandChannelState = normalize(commandChannelState);
            feedbackChannelState = normalize(feedbackChannelState);
            if (feedbackAgeMillis != null && feedbackAgeMillis < 0L) {
                throw new IllegalArgumentException(
                        "feedbackAgeMillis must not be negative"
                );
            }
            if (bytesReceived < 0L || validFrames < 0L || droppedFrames < 0L) {
                throw new IllegalArgumentException(
                        "Telemetry counters must not be negative"
                );
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
