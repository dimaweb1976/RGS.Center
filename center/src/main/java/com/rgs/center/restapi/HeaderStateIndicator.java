package com.rgs.center.restapi;

import com.rgs.center.CenterWarehouse;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.OperationalState;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class HeaderStateIndicator {
    
    private final DashboardSnapshotService dashboardSnapshotService;
    
    private static final Logger log = LoggerFactory.getLogger( ActionRunButtonsState.class);
    
    @Autowired
    private CenterWarehouse warehouse;
    
    public HeaderStateIndicator( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    @GetMapping("/v1/MainIndicator")
    public ResponseEntity<?> aboutLines() {
        
        Object runState = warehouse.getWareHouseItem( "isRunning");
        
        int nColor = 3;
        String strStatus = "FAULT";
        String strDescription = "no data";
        String strSerialNum = "RGS sn: 2534-26";
        
        switch( runState) {
            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IDLE:
                nColor = 1; strStatus = "Idle";     strDescription = "";    break;

            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_AUTOMODE:
                nColor = 2; strStatus = "Auto mode";strDescription = "Running"; break;
            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_AUTOMODE_PAUSE:
                nColor = 2; strStatus = "Auto mode";strDescription = "Paused";  break;
                
            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_MANUALMODE:
                nColor = 2; strStatus = "Manual mode";strDescription = "Running";   break;
            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_MANUALMODE_PAUSE:
                nColor = 2; strStatus = "Manual mode";  strDescription = "Paused";  break;

            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_BG_ACQ:
                nColor = 2; strStatus = "Bg acquisition";   strDescription = "Paused";  break;

            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_BG_ACQ_PAUSE:
                nColor = 2; strStatus = "Bg acquisition";   strDescription = "Paused";  break;
            
            case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_FAULT:
                nColor = 3; strStatus = "Fault";    strDescription = "fault";   break;
                
            default:
                log.error( "unknown complex state");
        }
        
        nColor =( int) ( Math.random() * 5.);
        
        return ResponseEntity.ok( 
                Map.of(
                    "color", nColor,
                    "status", strStatus,
                    "description", strDescription,
                    "sernum", strSerialNum
                )
            );
    }
}
