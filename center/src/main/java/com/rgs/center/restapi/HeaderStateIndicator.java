package com.rgs.center.restapi;

import com.rgs.center.warehouse.RGSCenterWarehouse;
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
    
    private static final Logger log = LoggerFactory.getLogger( ActionRunButtonsState.class);
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    public HeaderStateIndicator( ) {
    }
    
    @GetMapping("/v1/MainIndicator")
    public ResponseEntity<?> aboutLines() {
        
        Object runState = warehouse.getItem( "device.run.state");
        
        int nColor = 3;
        String strStatus = "FAULT";
        String strDescription = "no data";
        String strSerialNum = "RGS sn: 2534-26";
        
        switch( runState) {
            case RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_IDLE:
                nColor = 1; strStatus = "Idle";     strDescription = "";    break;

            case RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_AUTO:
                nColor = 2; strStatus = "Auto mode";strDescription = "Running"; break;
            //case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_AUTOMODE_PAUSE:
            //    nColor = 2; strStatus = "Auto mode";strDescription = "Paused";  break;
                
            case RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_MANUAL:
                nColor = 2; strStatus = "Manual mode";strDescription = "Running";   break;
            //case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_MANUALMODE_PAUSE:
            //    nColor = 2; strStatus = "Manual mode";  strDescription = "Paused";  break;

            case RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_BG_ACQ:
                nColor = 2; strStatus = "Bg acquisition";   strDescription = "Paused";  break;

            //case CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_BG_ACQ_PAUSE:
            //    nColor = 2; strStatus = "Bg acquisition";   strDescription = "Paused";  break;
            
            case RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_FAIL:
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
