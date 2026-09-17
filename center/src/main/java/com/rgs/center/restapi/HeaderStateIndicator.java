package com.rgs.center.restapi;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class HeaderStateIndicator {
    
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public HeaderStateIndicator( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    @GetMapping("/v1/MainIndicator")
    public ResponseEntity<?> aboutLines() {
        return ResponseEntity.ok( 
                Map.of(
                    "color", 1
                )
            );
    }
}
