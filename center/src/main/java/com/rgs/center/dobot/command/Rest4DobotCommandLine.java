package com.rgs.center.dobot.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dobot")
public class Rest4DobotCommandLine {

    @Autowired
    private ServiceTcpDobotCommandConnection dobotConnection;

    @GetMapping("/command/status")
    public String getStatus() {
        boolean running = dobotConnection.isRunning();
        return "{\"running\": " + running + "}";
    }

    @PostMapping("/command/start")
    public String startService() {
        dobotConnection.start();
        return "{\"status\": \"started\"}";
    }

    @PostMapping("/command/stop")
    public String stopService() {
        dobotConnection.stop();
        return "{\"status\": \"stopped\"}";
    }
}