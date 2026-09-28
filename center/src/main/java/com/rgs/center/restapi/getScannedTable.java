package com.rgs.center.restapi;

import com.rgs.center.camera.CameraImagingService;
import com.rgs.center.camera.CameraImagingSnapshot;
import com.rgs.center.camera.DetectedBottle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class getScannedTable {
    private static final Logger log = LoggerFactory.getLogger(RestApiCamerasReadiness.class);
    
    // Frame resolution (standard camera resolution or frame bounds)
    private static final double FRAME_WIDTH = 1920.0;
    private static final double FRAME_HEIGHT = 1080.0;

    private final CameraImagingService cameraImagingService;

    
    public getScannedTable(CameraImagingService cameraImagingService) {
        this.cameraImagingService = cameraImagingService;
    }
    
    
    @GetMapping("/api/getScannedTable")
    public ResponseEntity<?> buttonsState() {
        // 1. Capture current frame from camera
        cameraImagingService.capture();

        // 2. Perform BoofCV recognition
        CameraImagingSnapshot snapshot = cameraImagingService.recognize();

        List<DetectedBottle> detectedBottles = snapshot.bottles();
        List<Map<String, Object>> result = new ArrayList<>();

        // 3. Convert pixel coordinates to percentage for frontend map
        for (DetectedBottle bottle : detectedBottles) {
            double percentX = (bottle.centerX() / FRAME_WIDTH) * 100.0;
            double percentY = (bottle.centerY() / FRAME_HEIGHT) * 100.0;

            result.add(Map.of(
                    "id", String.format("S-%03d", bottle.number()),
                    "x", Math.round(percentX * 10.0) / 10.0,
                    "y", Math.round(percentY * 10.0) / 10.0,
                    "mass", "0.0 g"
            ));
        }

        log.info("Detected {} bottles via BoofCV", result.size());
        return ResponseEntity.ok(result);
    }
}
