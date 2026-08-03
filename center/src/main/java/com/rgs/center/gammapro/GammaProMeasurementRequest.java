package com.rgs.center.gammapro;

/**
 * Параметры 7-го этапа автоматического сценария - набора спектра.
 *
 * @param mcaName имя анализатора в менеджере устройств Gamma-PRO
 * @param channelName имя спектрометрического канала
 * @param presetSeconds продолжительность измерения, с
 */
public record GammaProMeasurementRequest(
        String mcaName,
        String channelName,
        int presetSeconds
) {

    public GammaProMeasurementRequest {
        GammaProProtocol.requireParameter(mcaName, "MCA name");
        GammaProProtocol.requireParameter(channelName, "Channel name");
        if (presetSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Measurement preset must be positive"
            );
        }
    }
}
