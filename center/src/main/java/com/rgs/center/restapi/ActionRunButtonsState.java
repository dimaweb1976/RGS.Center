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
        
        boolean b = Math.random() > 0.5;
        
        boolean b2 = Math.random() > 0.5;
        
        return ResponseEntity.ok( 
                Map.ofEntries(
                    Map.entry( "AutoStart", b),
                    Map.entry( "AutoPause", !b),
                    Map.entry( "AutoStop", !b),                    
                    Map.entry( "ManualStart", b),
                    Map.entry( "ManualPause", !b),
                    Map.entry( "ManualStop", !b),
                    Map.entry( "ManualOpenShield", b & b2),
                    Map.entry( "ManualCloseShield", b & !b2)
                )
            );
    }
}
