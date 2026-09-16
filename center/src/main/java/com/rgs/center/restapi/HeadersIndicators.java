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
public class HeadersIndicators {
    
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public HeadersIndicators( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    @GetMapping("/v1/indicators")
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
                Map.of(
                    "plc", strPlcStatus, //resolveStatus( spshot.equipment().manipulator().health()),
                    "gammapro", strGammaProStatus, //resolveStatus( spshot.equipment().gammaPro().health()),
                    "shield", strShieldStatus,
                    "detector", strDetectorStatus, //resolveStatus( spshot.equipment().shp().health()),
                    "MCA", strMcaStatus, //"UNDEFINED",
                    "cooler", strCoolerStatus //resolveStatus( spshot.equipment().nicole().health())
                )
            );
    }
}
