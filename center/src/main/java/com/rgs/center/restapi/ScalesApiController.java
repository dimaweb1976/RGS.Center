package com.rgs.center.restapi;

import com.rgs.center.scales.ScalesReading;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.scales.ScalesSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/scales")
public class ScalesApiController {

    private final ScalesService scalesService;

    public ScalesApiController(ScalesService scalesService) {
        this.scalesService = scalesService;
    }

    /* Trigger active weighing */
    @PostMapping("/weigh")
    public ResponseEntity<?> weigh() {
        try {
            ScalesReading reading = scalesService.readWeight();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mass", reading.mass(),
                    "unit", reading.unit(),
                    "massGrams", reading.massGrams(),
                    "stability", reading.stability().name()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "Weighing failed"
            ));
        }
    }
    
    /* Check hardware availability */
    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability() {
        ScalesSnapshot snapshot = scalesService.snapshot();
        return ResponseEntity.ok(Map.of(
                "available", snapshot.connected()
        ));
    }
}