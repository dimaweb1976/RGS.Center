package com.rgs.center.measurement.manual;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Installation-specific commands and limits for manual measurements. */
@Component
@ConfigurationProperties(prefix = "rgs.measurement.manual")
public class ManualMeasurementConfiguration {

    private Duration operationTimeout = Duration.ofMinutes(2);
    private String openHatchCommand = "";
    private String closeHatchCommand = "";
    private int highVoltage = 1000;

    public Duration getOperationTimeout() { return operationTimeout; }
    public void setOperationTimeout(Duration value) { operationTimeout = value; }
    public String getOpenHatchCommand() { return openHatchCommand; }
    public void setOpenHatchCommand(String value) { openHatchCommand = normalize(value); }
    public String getCloseHatchCommand() { return closeHatchCommand; }
    public void setCloseHatchCommand(String value) { closeHatchCommand = normalize(value); }
    public int getHighVoltage() { return highVoltage; }
    public void setHighVoltage(int value) { highVoltage = value; }

    public void validate() {
        if (operationTimeout == null || operationTimeout.isZero()
                || operationTimeout.isNegative()) {
            throw new IllegalStateException("Manual operation timeout must be positive");
        }
        if (highVoltage < 0) {
            throw new IllegalStateException("Manual high voltage must not be negative");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
