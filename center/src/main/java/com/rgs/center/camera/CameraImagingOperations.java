package com.rgs.center.camera;

import java.util.Optional;

/** Контракт операций окна камеры, отделённый от эмулятора сценария. */
public interface CameraImagingOperations {

    CameraImagingSnapshot capture();

    CameraImagingSnapshot recognize();

    CameraImagingSnapshot snapshot();

    Optional<CameraImagePayload> currentImage();

    void reset();
}
