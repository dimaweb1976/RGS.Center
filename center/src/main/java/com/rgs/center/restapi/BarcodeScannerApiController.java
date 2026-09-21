package com.rgs.center.restapi;

import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.barcodescanner.BarcodeScannerSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/barcode")
public class BarcodeScannerApiController {

    private final BarcodeScannerService scannerService;

    public BarcodeScannerApiController(BarcodeScannerService scannerService) {
        this.scannerService = scannerService;
    }

    /* Trigger scan operation */
    @PostMapping("/scan")
    public ResponseEntity<?> triggerScan() {
        boolean started = scannerService.startRead();
        return ResponseEntity.ok(Map.of("started", started));
    }

    /* Poll scan state and value */
    @GetMapping("/value")
    public ResponseEntity<?> getScanResult() {
        BarcodeScannerSnapshot snapshot = scannerService.snapshot();
        boolean active = scannerService.isReadActive();
        
        return ResponseEntity.ok(Map.of(
                "active", active,
                "barcode", snapshot.lastBarcode() != null ? snapshot.lastBarcode() : "",
                "status", snapshot.operationState() != null ? snapshot.operationState().name() : ""
        ));
    }
    
    /* Check barcode scanner hardware availability */
    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability() {
        BarcodeScannerSnapshot snapshot = scannerService.snapshot();
        return ResponseEntity.ok(Map.of(
                "available", snapshot.connected()
        ));
    }
}