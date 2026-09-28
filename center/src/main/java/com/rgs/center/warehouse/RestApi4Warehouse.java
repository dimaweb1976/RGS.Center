package com.rgs.center.warehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@RestController
public class RestApi4Warehouse {

    @Autowired
    private RGSCenterWarehouse warehouse;

    @GetMapping("/api/warehouse/all")
    public ResponseEntity<Map<String, Object>> getAllWarehouseItems() {
        Map<String, Object> sortedMap = warehouse.warehouse.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        return ResponseEntity.ok(sortedMap);
    }
}