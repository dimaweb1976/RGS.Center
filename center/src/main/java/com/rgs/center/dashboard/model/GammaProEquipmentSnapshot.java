package com.rgs.center.dashboard.model;

import java.util.List;
import java.util.Objects;
import java.time.Instant;

/**
 * Состояние TCP-сервера Gamma-PRO и текущего набора спектра.
 *
 * <p>Наблюдения для индикатора хранятся в общей витрине, без второго
 * независимо обновляемого состояния. {@code heartbeatFresh} вычисляется
 * сборщиком относительно времени снимка; отметка heartbeat относится только
 * к текущему TCP-сеансу. Активная бизнес-команда не включает heartbeat.
 * {@code responseUncertain} отличает тайм-аут от отрицательного ответа ER.
 * Во время измерения актуальность определяется {@code measurementDeadlineAt},
 * поскольку WAIT_MEAS_END занимает канал и запрещает параллельный heartbeat.
 * Эти признаки не подтверждают подключение физического BOSON или его HV.
 */
public record GammaProEquipmentSnapshot(
        DeviceHealth health,
        String host,
        int port,
        SpectrumAcquisitionState acquisitionState,
        String mcaName,
        String channelName,
        Integer presetSeconds,
        List<String> tracts,
        String lastCommand,
        String lastResponse,
        boolean connectionAttempted,
        Instant lastSuccessfulHeartbeatAt,
        boolean heartbeatFresh,
        boolean businessCommandActive,
        boolean responseUncertain,
        Instant measurementDeadlineAt
) {
    public GammaProEquipmentSnapshot {
        Objects.requireNonNull(health, "health");
        host = normalize(host);
        Objects.requireNonNull(acquisitionState, "acquisitionState");
        mcaName = normalize(mcaName);
        channelName = normalize(channelName);
        tracts = List.copyOf(Objects.requireNonNull(tracts, "tracts"));
        lastCommand = normalize(lastCommand);
        lastResponse = normalize(lastResponse);
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port is outside TCP range");
        }
        if (presetSeconds != null && presetSeconds < 0) {
            throw new IllegalArgumentException(
                    "presetSeconds must not be negative"
            );
        }
    }

    /** Совместимость со снимками без новых диагностических наблюдений. */
    public GammaProEquipmentSnapshot(DeviceHealth health, String host, int port,
            SpectrumAcquisitionState acquisitionState, String mcaName,
            String channelName, Integer presetSeconds, List<String> tracts,
            String lastCommand, String lastResponse) {
        this(health, host, port, acquisitionState, mcaName, channelName,
                presetSeconds, tracts, lastCommand, lastResponse,
                false, null, false, false, true, null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    /** Нормализованное состояние набора спектра. */
    public enum SpectrumAcquisitionState {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        COMPLETED,
        FAILED,
        UNKNOWN
    }
}
