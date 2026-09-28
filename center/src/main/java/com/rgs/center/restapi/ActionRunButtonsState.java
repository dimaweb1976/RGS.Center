package com.rgs.center.restapi;

import com.rgs.center.dobot.ServiceTcpDobotStreamLineConnection;
import com.rgs.center.gammapro.ServiceTcpGammaProConnection;
import com.rgs.center.nicole.ServiceTcpNicoleConnection;
import com.rgs.center.shp.ServiceShpConnection;
import com.rgs.center.warehouse.RGSCenterWarehouse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class ActionRunButtonsState {
    
    private static final Logger log = LoggerFactory.getLogger( ActionRunButtonsState.class);
    
    
    @Autowired
    ServiceTcpNicoleConnection serviceNicole;
    
    @Autowired
    ServiceTcpDobotStreamLineConnection serviceDobot;
    
    @Autowired
    ServiceTcpGammaProConnection serviceGammaPro;
    
    //@Autowired
    //ServiceTcpCamerasConnection serviceCameras;
    
    @Autowired
    ServiceShpConnection serviceShp;
    
    @Autowired
    RGSCenterWarehouse warehouse;
    
    public ActionRunButtonsState() {
    }
    
    @GetMapping("/api/v1/buttonsState")
    public ResponseEntity<?> buttonsState() {
        
        
        Object isRunning = warehouse.getItem( "device.run.state");
        boolean deviceIsBusy = (
                isRunning == RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_AUTO ||
                isRunning == RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_MANUAL ||
                isRunning == RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_BG_ACQ
                );
        
        boolean bDeviceAllModulesConnected;
        
        bDeviceAllModulesConnected  = ( serviceDobot.isActive());
        
        //bDeviceAllModulesConnected &= ( serviceCameras.isActive);
        
        bDeviceAllModulesConnected &= serviceGammaPro.isActive();
        bDeviceAllModulesConnected &= serviceNicole.isActive();
        bDeviceAllModulesConnected &= ( serviceShp.isActive() == 0);
            
        boolean bAutoStart = bDeviceAllModulesConnected & !deviceIsBusy;
        boolean bAutoPause = bDeviceAllModulesConnected & deviceIsBusy;
            
        return ResponseEntity.ok( 
                    Map.ofEntries(
                        Map.entry( "AutoStart", bAutoStart),
                        Map.entry( "AutoPause", bAutoPause),
                        Map.entry( "AutoStop",      bDeviceAllModulesConnected & deviceIsBusy),
                        
                        Map.entry( "ManualStart",   bDeviceAllModulesConnected & !deviceIsBusy),
                        Map.entry( "ManualPause",   bDeviceAllModulesConnected),
                        Map.entry( "ManualStop",    bDeviceAllModulesConnected),
                        Map.entry( "ManualOpenShield",  bDeviceAllModulesConnected),
                        Map.entry( "ManualCloseShield", bDeviceAllModulesConnected)
                    )
        );
    }
}
