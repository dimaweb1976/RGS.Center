package com.rgs.center.restapi;

import com.rgs.center.CenterWarehouse;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class RestApiCamerasReadiness {
    
    private static final Logger log = LoggerFactory.getLogger(RestApiCamerasReadiness.class);
    
    @Autowired
    private CenterWarehouse warehouse;
            
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public RestApiCamerasReadiness( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    
    @GetMapping("/api/v1/camerasReady")
    public ResponseEntity<?> buttonsState() {
        DashboardSnapshot spshot = dashboardSnapshotService.snapshot();
        
        boolean bCam1Connected = spshot.equipment().cameras().get( 0).health().connection().equals( ConnectionState.ONLINE);
        boolean bCam2Connected = true;
        //boolean bCam2Connected = spshot.equipment().cameras().get( 1).health().connection().equals( ConnectionState.ONLINE);
            
        return ResponseEntity.ok( 
                    Map.ofEntries(
                        Map.entry( "camerasReady", bCam1Connected & bCam2Connected)
                    )
        );
    }
}
