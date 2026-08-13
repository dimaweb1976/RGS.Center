package com.rgs.center.camera;

import java.util.List;

/** Неизменяемое состояние окна камеры и последнего распознавания. */
public record CameraImagingSnapshot(
        boolean imageAvailable,
        long imageVersion,
        boolean recognized,
        String message,
        String originalFile,
        String detectedFile,
        List<DetectedBottle> bottles
) {
}
