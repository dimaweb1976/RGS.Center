package com.rgs.center.measurement.emulator;

import com.rgs.center.camera.DetectedBottle;

import java.util.List;

/** Неизменяемый снимок эмулятора для Thymeleaf-представления. */
public record MeasurementEmulatorSnapshot(
        MeasurementEmulatorPhase phase,
        String phaseLabel,
        String alert,
        boolean startEnabled,
        String highVoltageState,
        double leakageCurrentMicroamps,
        double leakageLimitMicroamps,
        boolean cameraImageVisible,
        long cameraImageVersion,
        boolean cameraRecognized,
        String cameraResultMessage,
        String cameraOriginalFile,
        String cameraDetectedFile,
        List<DetectedBottle> detectedBottles,
        int activeObjectNumber,
        int completedObjects,
        List<EmulatorCheck> deviceChecks,
        List<EmulatorCheck> initialStateChecks,
        List<EmulatorCheck> controlParameterChecks,
        List<EmulatedMeasurementObject> objects,
        List<EmulatorCycleStep> cycleSteps,
        List<String> events
) {
}
