package com.rgs.center.restapi;

import com.rgs.center.scales.ScalesServiceRightOne;
import com.rgs.center.warehouse.RGSCenterWarehouse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/scales")
public class ScalesApiController {

    @Autowired
    RGSCenterWarehouse warehouse;
    
    @Autowired
    ScalesServiceRightOne serviceScales;
    
    public ScalesApiController() {
    }

    /* Trigger active weighing */
    @PostMapping("/weigh")
    public ResponseEntity<?> weigh() {
        Object o = warehouse.getItem( RGSCenterWarehouse.DEVICES_SCALES_MASS);
        double dblMass = -11.11;
        if( o != null) {
            if( o instanceof double) {
                dblMass = ( double) o;
            }
        }
        
        return ResponseEntity.ok(
                Map.of( "mass", dblMass)
        );
        /*
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
        }*/
    }
    
    /* Check hardware availability */
    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability() {
        
        //ScalesSnapshot snapshot = scalesService.snapshot();
        return ResponseEntity.ok(Map.of(
                "available", serviceScales.isRunning()
        ));
    }
}