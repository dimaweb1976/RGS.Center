package com.rgs.center.dashboard.model;

import java.util.List;
import java.util.Objects;

/** Состояние TCP-сервера Gamma-PRO и текущего набора спектра. */
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
        String lastResponse
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
