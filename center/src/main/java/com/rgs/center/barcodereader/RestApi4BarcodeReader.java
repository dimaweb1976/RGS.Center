package com.rgs.center.barcodereader;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/barcode")
public class RestApi4BarcodeReader {

    @Autowired
    private BarcodeService barcodeService;

    @Autowired
    private RGSCenterWarehouse warehouse;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        boolean isRunning = barcodeService.isRunning();
        boolean isScanning = barcodeService.isScanningActive();
        Object lastCode = warehouse.getItem(RGSCenterWarehouse.DEVICES_BARCODE_READER_LAST_CODE);
        
        return ResponseEntity.ok(Map.of(
            "running", isRunning,
            "scanning", isScanning,
            "lastBarcode", lastCode != null ? lastCode.toString() : ""
        ));
    }

    @PostMapping("/start")
    public ResponseEntity<Void> startService() {
        barcodeService.start(50);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stopService() {
        barcodeService.stop();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read")
    public ResponseEntity<Void> triggerRead() {
        barcodeService.triggerRead();
        return ResponseEntity.ok().build();
    }
}