package com.rgs.center.restapi;

import com.rgs.center.barcodereader.BarcodeService;
import com.rgs.center.warehouse.RGSCenterWarehouse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/barcode")
public class BarcodeScannerApiController {

    @Autowired
    BarcodeService serviceBarcodeReader;

    @Autowired
    private RGSCenterWarehouse warehouse;
    
    public BarcodeScannerApiController() {
    }

    /* Trigger scan operation */
    @PostMapping("/scan")
    public ResponseEntity<?> triggerScan() {
        serviceBarcodeReader.triggerRead();
        return ResponseEntity.ok(Map.of("started", true));
    }

    /* Poll scan state and value */
    @GetMapping("/value")
    public ResponseEntity<?> getScanResult() {
        Object lastCode = warehouse.getItem(RGSCenterWarehouse.DEVICES_BARCODE_READER_LAST_CODE);        
        return ResponseEntity.ok(Map.of(
                "barcode", lastCode != null ? lastCode.toString() : ""
        ));
    }
    
    /* Check barcode scanner hardware availability */
    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability() {
        return ResponseEntity.ok(Map.of(
                "available", serviceBarcodeReader.isActive()
        ));
    }
}