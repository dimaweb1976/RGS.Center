package com.rgs.center.camera;

/** Ошибка получения, обработки или сохранения изображения камеры. */
public class CameraImagingException extends RuntimeException {

    public CameraImagingException(String message) {
        super(message);
    }

    public CameraImagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
