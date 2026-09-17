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

public class ActionRunButtonsState {
    
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public ActionRunButtonsState( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    @GetMapping("/api/v1/buttonsState")
    public ResponseEntity<?> aboutLines() {
        DashboardSnapshot spshot = dashboardSnapshotService.snapshot();
        
        return ResponseEntity.ok( 
                Map.ofEntries(
                    Map.entry( "AutoStart", "false"),
                    Map.entry( "AutoPause", "false"),
                    Map.entry( "AutoStop", "false"),                    
                    Map.entry( "ManualStart", "false"),
                    Map.entry( "ManualPause", "false"),
                    Map.entry( "ManualStop", "false"),
                    Map.entry( "ManualOpenShield", "false"),
                    Map.entry( "ManualCloseShield", "false")
                )
            );
    }
}
