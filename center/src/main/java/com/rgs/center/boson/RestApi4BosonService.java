package com.rgs.center.boson;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/boson")
public class RestApi4BosonService {

    @Autowired
    private ServiceTcpBosonConnection bosonConnection;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getStatus() {
        boolean isRunning = bosonConnection.isRunning();
        return ResponseEntity.ok(Map.of("running", isRunning));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startService() {
        bosonConnection.start(200, 1000);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopService() {
        bosonConnection.stop();
        return ResponseEntity.ok().build();
    }
}