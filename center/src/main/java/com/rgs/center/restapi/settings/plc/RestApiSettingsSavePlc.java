package com.rgs.center.restapi.settings.plc;

import com.rgs.center.settings.RGSCenterSettings;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class RestApiSettingsSavePlc {
    
    @Autowired
    private RGSCenterSettings settings;
    
    public RestApiSettingsSavePlc( ) {
    }
    
    @GetMapping("/v1/settings/save/plc")
    public ResponseEntity<?> savePlcSettings() {
        
        return ResponseEntity.ok(Map.of(
            "saved", "ok")
        );
    }
}
