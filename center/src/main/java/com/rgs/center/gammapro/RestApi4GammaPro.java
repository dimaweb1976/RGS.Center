package com.rgs.center.gammapro;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/gammapro")
public class RestApi4GammaPro {

    @Autowired
    private ServiceTcpGammaProConnection gammaProConnection;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getStatus() {
        boolean isRunning = gammaProConnection.isActive();
        return ResponseEntity.ok(Map.of("running", isRunning));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startService() {
        gammaProConnection.start(200, 1000);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopService() {
        gammaProConnection.stop();
        return ResponseEntity.ok().build();
    }
}