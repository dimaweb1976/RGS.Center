package com.rgs.center.api.manipulator;

import com.rgs.center.manipulator.ManipulatorConnectionStatus;
import com.rgs.center.manipulator.ManipulatorReadinessStatus;
import com.rgs.center.manipulator.SpectrometerHatchStatus;

import java.time.Instant;
import java.util.List;

/** Полный read-only snapshot манипулятора для web-интерфейсов. */
public record ManipulatorStatusResponse(
        Instant generatedAt,
        boolean enabled,
        String host,
        int commandPort,
        int feedbackPort,
        ManipulatorConnectionStatus connectionStatus,
        ManipulatorReadinessStatus technicalStatus,
        List<String> statusReasons,
        Pose pose,
        Motion motion,
        Io io,
        Hatch hatch,
        Safety safety,
        Telemetry telemetry,
        String lastError,
        boolean feedbackOnly
) {
    public ManipulatorStatusResponse {
        statusReasons = List.copyOf(statusReasons);
    }

    /** Текущие декартовы координаты TCP. */
    public record Pose(
            Double x,
            Double y,
            Double z,
            Double rx,
            Double ry,
            Double rz
    ) {
    }

    /** Состояние выполнения команд роботом. */
    public record Motion(
            Long robotMode,
            Boolean enabled,
            Boolean running,
            Boolean queuedCommandRunning,
            Boolean error,
            Long currentCommandId
    ) {
    }

    /** Полные маски дискретных входов и выходов. */
    public record Io(
            String digitalInputsHex,
            String digitalOutputsHex
    ) {
    }

    /** Концевики и независимое измерение аналогового положения люка. */
    public record Hatch(
            SpectrometerHatchStatus status,
            Integer closedInputBit,
            Boolean closedLimit,
            Integer openInputBit,
            Boolean openLimit,
            boolean analogEnabled,
            int analogInputIndex,
            Double analogValue,
            Instant analogReceivedAt,
            String analogLastError
    ) {
    }

    /** Raw и декодированные active-low safety-сигналы. */
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
            blockingFaults = List.copyOf(blockingFaults);
        }
    }

    /** Диагностика транспортных потоков и актуальности кадра. */
    public record Telemetry(
            String commandChannelState,
            String feedbackChannelState,
            Long controllerTimestampMillis,
            Instant receivedAt,
            Long ageMillis,
            long bytesReceived,
            long validFrames,
            long droppedFrames
    ) {
    }
}
