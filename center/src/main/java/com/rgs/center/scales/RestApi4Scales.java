package com.rgs.center.scales;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scales")
public class RestApi4Scales {

    @Autowired
    private ScalesServiceRightOne scalesService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getStatus() {
        boolean isRunning = scalesService.isRunning();
        return ResponseEntity.ok(Map.of("running", isRunning));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startService() {
        scalesService.start(100, 1000);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopService() {
        scalesService.stop();
        return ResponseEntity.ok().build();
    }
}