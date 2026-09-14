package com.rgs.center.measurement.workflow;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runtime limits and installation-specific Dobot command templates. */
@Component
@ConfigurationProperties(prefix = "rgs.measurement.workflow")
public class MeasurementWorkflowConfiguration {

    private int maxAttempts = 2;
    private Duration operationTimeout = Duration.ofMinutes(2);
    private Duration spectrumOverrun = Duration.ofMinutes(2);
    private Duration gammaPollInterval = Duration.ofMillis(500);
    private Duration estimatedDeviceOperationDuration = Duration.ofSeconds(30);
    private final Manipulator manipulator = new Manipulator();

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    public void setOperationTimeout(Duration operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    public Duration getSpectrumOverrun() {
        return spectrumOverrun;
    }

    public void setSpectrumOverrun(Duration spectrumOverrun) {
        this.spectrumOverrun = spectrumOverrun;
    }

    public Duration getGammaPollInterval() {
        return gammaPollInterval;
    }

    public void setGammaPollInterval(Duration gammaPollInterval) {
        this.gammaPollInterval = gammaPollInterval;
    }

    public Duration getEstimatedDeviceOperationDuration() {
        return estimatedDeviceOperationDuration;
    }

    public void setEstimatedDeviceOperationDuration(Duration value) {
        estimatedDeviceOperationDuration = value;
    }

    public Manipulator getManipulator() {
        return manipulator;
    }

    public void validate() {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalStateException("Workflow maxAttempts must be between 1 and 10");
        }
        requirePositive(operationTimeout, "operationTimeout");
        requirePositive(spectrumOverrun, "spectrumOverrun");
        requirePositive(gammaPollInterval, "gammaPollInterval");
        requirePositive(
                estimatedDeviceOperationDuration,
                "estimatedDeviceOperationDuration"
        );
        if (gammaPollInterval.toMillis() < 1L) {
            throw new IllegalStateException(
                    "Workflow gammaPollInterval must be at least 1 ms"
            );
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("Workflow " + name + " must be positive");
        }
    }

    /**
     * Commands are blank until the installation PLC protocol is approved.
     * Pick/return templates may contain {number}, {x} and {y} tokens.
     */
    public static class Manipulator {
        private String pickCommandTemplate = "";
        private String moveToBarcodeCommand = "";
        private String moveToScalesCommand = "";
        private String moveToSpectrometerCommand = "";
        private String removeFromSpectrometerCommand = "";
        private String returnCommandTemplate = "";

        public String getPickCommandTemplate() { return pickCommandTemplate; }
        public void setPickCommandTemplate(String value) { pickCommandTemplate = normalize(value); }
        public String getMoveToBarcodeCommand() { return moveToBarcodeCommand; }
        public void setMoveToBarcodeCommand(String value) { moveToBarcodeCommand = normalize(value); }
        public String getMoveToScalesCommand() { return moveToScalesCommand; }
        public void setMoveToScalesCommand(String value) { moveToScalesCommand = normalize(value); }
        public String getMoveToSpectrometerCommand() { return moveToSpectrometerCommand; }
        public void setMoveToSpectrometerCommand(String value) { moveToSpectrometerCommand = normalize(value); }
        public String getRemoveFromSpectrometerCommand() { return removeFromSpectrometerCommand; }
        public void setRemoveFromSpectrometerCommand(String value) { removeFromSpectrometerCommand = normalize(value); }
        public String getReturnCommandTemplate() { return returnCommandTemplate; }
        public void setReturnCommandTemplate(String value) { returnCommandTemplate = normalize(value); }

        public boolean isComplete() {
            return !pickCommandTemplate.isBlank()
                    && !moveToBarcodeCommand.isBlank()
                    && !moveToScalesCommand.isBlank()
                    && !moveToSpectrometerCommand.isBlank()
                    && !removeFromSpectrometerCommand.isBlank()
                    && !returnCommandTemplate.isBlank();
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
