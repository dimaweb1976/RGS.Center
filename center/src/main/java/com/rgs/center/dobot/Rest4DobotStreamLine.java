package com.rgs.center.dobot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dobot")
public class Rest4DobotStreamLine {

    @Autowired
    private ServiceTcpDobotStreamLineConnection dobotConnection;

    @GetMapping("/stream/status")
    public String getStatus() {
        boolean running = dobotConnection.isRunning();
        return "{\"running\": " + running + "}";
    }

    @PostMapping("/stream/start")
    public String startService() {
        dobotConnection.start( 100);
        return "{\"status\": \"started\"}";
    }

    @PostMapping("/stream/stop")
    public String stopService() {
        dobotConnection.stop();
        return "{\"status\": \"stopped\"}";
    }
}