package com.rgs.center.nicole;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/nicole")
public class RestApi4Nicole {

    @Autowired
    private ServiceTcpNicoleConnection nicoleConnection;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getStatus() {
        boolean isRunning = nicoleConnection.isActive();
        return ResponseEntity.ok(Map.of("running", isRunning));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startService() {
        nicoleConnection.start(200, 1000);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopService() {
        nicoleConnection.stop();
        return ResponseEntity.ok().build();
    }
}