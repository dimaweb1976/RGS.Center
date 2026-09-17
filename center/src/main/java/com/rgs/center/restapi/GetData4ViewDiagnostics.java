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
public class GetData4ViewDiagnostics {
    
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public GetData4ViewDiagnostics( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    @GetMapping("/v1/diagnostics")
    public ResponseEntity<?> aboutLines() {
        DashboardSnapshot spshot = dashboardSnapshotService.snapshot();
        
        
        
        String strPlcStatus = "UNKNOWN";
        switch( spshot.equipment().manipulator().health().connection()) {
            case ConnectionState.DISABLED:
                strPlcStatus = "DISABLED";      break;
            case ConnectionState.CONNECTING:
                strPlcStatus = "CONNECTING";    break;
            case ConnectionState.ONLINE:
                strPlcStatus = "ONLINE";
                if( spshot.equipment().manipulator().health().operation() == OperationalState.RUNNING)
                    strPlcStatus = "IN PROGRESS";
                break;                
            case ConnectionState.OFFLINE:
                strPlcStatus = "OFFLINE";       break;
        }
        
        String strGammaProStatus = "UNKNOWN";
        switch( spshot.equipment().gammaPro().health().connection()) {
            case ConnectionState.DISABLED:
                strGammaProStatus = "DISABLED";      break;
            case ConnectionState.CONNECTING:
                strGammaProStatus = "CONNECTING";    break;
            case ConnectionState.ONLINE:
                strGammaProStatus = "ONLINE";
                if( spshot.equipment().manipulator().health().operation() == OperationalState.RUNNING)
                    strGammaProStatus = "IN PROGRESS";
                break;                
            case ConnectionState.OFFLINE:
                strGammaProStatus = "OFFLINE";       break;
        }
        
        String strShieldStatus = "UNKNOWN";
        
        String strDetectorStatus = "UNKNOWN";
        
        String strMcaStatus = "UNKNOWN";
        
        String strCoolerStatus = "UNKNOWN";
        switch( spshot.equipment().nicole().health().connection()) {
            case ConnectionState.DISABLED:
                strCoolerStatus = "DISABLED";      break;
            case ConnectionState.CONNECTING:
                strCoolerStatus = "CONNECTING";    break;
            case ConnectionState.ONLINE:
                strCoolerStatus = "ONLINE";
                if( spshot.equipment().nicole().health().operation() == OperationalState.RUNNING)
                    strCoolerStatus = "IN PROGRESS";
                break;                
            case ConnectionState.OFFLINE:
                strCoolerStatus = "OFFLINE";       break;
        }
        

        return ResponseEntity.ok( 
                Map.ofEntries(
                    Map.entry("plc", "OFFLINE"),
                    Map.entry("gripper", "Online"),
                    Map.entry("7axis", "Online"),
                    Map.entry("gammapro", "OFFLINE"),
                    Map.entry("shield", "Unknown"),
                    Map.entry("detector_hv", "HV OFF"),
                    Map.entry("detector_leakage", "-"),
                    Map.entry("detector_temperature", "−168"),
                    Map.entry("TurnHvOn", "true"),
                    Map.entry("TurnHvOff", "true"),
                    Map.entry("mca", "Unknown"),
                    Map.entry("cooling_system", "Unknown"),
                    Map.entry("ln2_lev", "" + 50 + ( int) (Math.random()*25.)),
                    Map.entry("ups", "" + 90 + ( int) (Math.random()*10.)),
                    Map.entry("camera1", "Online"),
                    Map.entry("camera2", "Online"),
                    Map.entry("scales", "Online"),
                    Map.entry("scanner", "Online")
                )
            );
    }
}
