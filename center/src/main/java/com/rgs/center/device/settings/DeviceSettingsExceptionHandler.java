package com.rgs.center.device.settings;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/** Stable JSON errors for the device-settings REST contract. */
@RestControllerAdvice(assignableTypes = DeviceSettingsController.class)
public class DeviceSettingsExceptionHandler {

    @ExceptionHandler(DeviceSettingsConflictException.class)
    ResponseEntity<?> conflict(DeviceSettingsConflictException exception) {
        return response(HttpStatus.CONFLICT, "SETTINGS_VERSION_CONFLICT", exception);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(NoSuchElementException exception) {
        return response(HttpStatus.NOT_FOUND, "DEVICE_OR_SETTING_NOT_FOUND", exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_DEVICE_SETTINGS", exception);
    }

    private static ResponseEntity<Map<String, String>> response(
            HttpStatus status,
            String code,
            RuntimeException exception
    ) {
        return ResponseEntity.status(status).body(Map.of(
                "error", code,
                "message", exception.getMessage()
        ));
    }
}
