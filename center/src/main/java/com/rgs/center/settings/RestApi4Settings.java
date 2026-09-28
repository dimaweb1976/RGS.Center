package com.rgs.center.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class RestApi4Settings {

    private final RGSCenterSettings settingsService;

    public RestApi4Settings(RGSCenterSettings settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/api/settings/all")
    public ResponseEntity<Map<String, Object>> getAllSettings() {
        return ResponseEntity.ok(settingsService.settings);
    }
}