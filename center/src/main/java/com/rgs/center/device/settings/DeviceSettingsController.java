package com.rgs.center.device.settings;

import com.rgs.center.auth.AuthPrincipal;
import com.rgs.center.connection.DeviceConnectionManager;
import com.rgs.center.connection.DeviceReconfigurationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authenticated browser API for reading, saving and applying device settings. */
@RestController
@RequestMapping("/api/device-settings")
public class DeviceSettingsController {

    private final DeviceSettingsService settingsService;
    private final DeviceConnectionManager connectionManager;

    public DeviceSettingsController(
            DeviceSettingsService settingsService,
            DeviceConnectionManager connectionManager
    ) {
        this.settingsService = settingsService;
        this.connectionManager = connectionManager;
    }

    @GetMapping
    public ResponseEntity<?> all(Authentication authentication) {
        ResponseEntity<?> denied = requireReader(authentication);
        if (denied != null) {
            return denied;
        }
        Map<String, List<SettingResponse>> grouped = new LinkedHashMap<>();
        settingsService.findAll().forEach(setting -> grouped
                .computeIfAbsent(setting.deviceCode(), ignored -> new java.util.ArrayList<>())
                .add(toResponse(setting)));
        return ResponseEntity.ok(grouped.entrySet().stream()
                .map(entry -> new DeviceResponse(entry.getKey(), List.copyOf(entry.getValue())))
                .toList());
    }

    @GetMapping("/{deviceCode}")
    public ResponseEntity<?> device(
            @PathVariable String deviceCode,
            Authentication authentication
    ) {
        ResponseEntity<?> denied = requireReader(authentication);
        if (denied != null) {
            return denied;
        }
        return ResponseEntity.ok(new DeviceResponse(
                deviceCode,
                settingsService.findByDevice(deviceCode).stream()
                        .map(DeviceSettingsController::toResponse)
                        .toList()
        ));
    }

    @PutMapping("/{deviceCode}")
    public ResponseEntity<?> update(
            @PathVariable String deviceCode,
            @RequestBody UpdateRequest request,
            Authentication authentication
    ) {
        AuthPrincipal administrator = administrator(authentication);
        if (administrator == null) {
            return writeDenied(authentication);
        }
        List<DeviceSettingsService.Update> updates = request == null
                || request.settings() == null
                ? null
                : request.settings().stream()
                        .map(value -> new DeviceSettingsService.Update(
                                value.stringId(), value.value(), value.version()
                        ))
                        .toList();
        List<SettingResponse> saved = settingsService.updateDevice(
                        deviceCode,
                        updates,
                        administrator.id()
                ).stream()
                .map(DeviceSettingsController::toResponse)
                .toList();
        return ResponseEntity.ok(new DeviceResponse(deviceCode, saved));
    }

    @PostMapping("/{deviceCode}/apply")
    public ResponseEntity<?> apply(
            @PathVariable String deviceCode,
            Authentication authentication
    ) {
        if (administrator(authentication) == null) {
            return writeDenied(authentication);
        }
        DeviceReconfigurationResult result = connectionManager.reconfigure(deviceCode);
        return result.applied()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    private static SettingResponse toResponse(DeviceSetting setting) {
        return new SettingResponse(
                setting.stringId(),
                setting.type().name(),
                setting.value(),
                setting.version(),
                setting.updatedAt(),
                setting.updatedBy(),
                setting.updatedByLogin()
        );
    }

    private static ResponseEntity<?> requireReader(Authentication authentication) {
        AuthPrincipal principal = principal(authentication);
        if (principal == null) {
            return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        }
        if (!isRole(principal, "administrator") && !isRole(principal, "operator")) {
            return error(HttpStatus.FORBIDDEN, "DEVICE_SETTINGS_READ_FORBIDDEN");
        }
        return null;
    }

    private static AuthPrincipal administrator(Authentication authentication) {
        AuthPrincipal principal = principal(authentication);
        return principal != null && isRole(principal, "administrator")
                ? principal
                : null;
    }

    private static ResponseEntity<?> writeDenied(Authentication authentication) {
        return principal(authentication) == null
                ? error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED")
                : error(HttpStatus.FORBIDDEN, "ADMINISTRATOR_REQUIRED");
    }

    private static AuthPrincipal principal(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal principal
                ? principal
                : null;
    }

    private static boolean isRole(AuthPrincipal principal, String role) {
        return role.equalsIgnoreCase(principal.roleName());
    }

    private static ResponseEntity<Map<String, String>> error(
            HttpStatus status,
            String code
    ) {
        return ResponseEntity.status(status).body(Map.of("error", code));
    }

    public record UpdateRequest(List<UpdateValue> settings) {
    }

    public record UpdateValue(String stringId, Object value, long version) {
    }

    public record DeviceResponse(String deviceCode, List<SettingResponse> settings) {
    }

    public record SettingResponse(
            String stringId,
            String type,
            Object value,
            long version,
            Instant updatedAt,
            Long updatedBy,
            String updatedByLogin
    ) {
    }
}
