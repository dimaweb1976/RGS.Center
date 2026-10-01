package com.rgs.center.shp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/shp")
public class RestApi4Shp {

    @Autowired
    private ServiceShpConnection shpConnection;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getStatus() {
        //isActive() возвращает 0, если всё запущено и работает штатно
        boolean isRunning = (shpConnection.isActive() == 0);
        return ResponseEntity.ok(Map.of("running", isRunning));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startService() {
        shpConnection.start(100, 1000);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopService() {
        shpConnection.stop();
        return ResponseEntity.ok().build();
    }
}