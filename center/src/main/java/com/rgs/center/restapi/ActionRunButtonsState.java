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

public class ActionRunButtonsState {
    
    private static final Logger log = LoggerFactory.getLogger( ActionRunButtonsState.class);
    
    @Autowired
    private CenterWarehouse warehouse;
            
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public ActionRunButtonsState( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    
    @GetMapping("/api/v1/buttonsState")
    public ResponseEntity<?> buttonsState() {
        DashboardSnapshot spshot = dashboardSnapshotService.snapshot();
        
        Object isRunning = warehouse.getWareHouseItem( "isRunning");
        boolean deviceIsBusy = (
                isRunning == CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_AUTOMODE ||
                isRunning == CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_MANUALMODE ||
                isRunning == CenterWarehouse.RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IN_BG_ACQ
                );
        
        boolean bDeviceAllModulesConnected;
        
        bDeviceAllModulesConnected  = ( spshot.equipment().manipulator().health().connection() == ConnectionState.ONLINE);
        
        bDeviceAllModulesConnected &= ( spshot.equipment().cameras().get( 0).health().connection() == ConnectionState.ONLINE);
        bDeviceAllModulesConnected &= ( spshot.equipment().cameras().get( 1).health().connection() == ConnectionState.ONLINE);
            
        bDeviceAllModulesConnected &= ( spshot.equipment().gammaPro().health().connection() == ConnectionState.ONLINE);
        bDeviceAllModulesConnected &= ( spshot.equipment().nicole().health().connection() == ConnectionState.ONLINE);
        bDeviceAllModulesConnected &= ( spshot.equipment().shp().health().connection() == ConnectionState.ONLINE);
        
            
            
        boolean bAutoStart = bDeviceAllModulesConnected & !deviceIsBusy;
        boolean bAutoPause = bDeviceAllModulesConnected & !deviceIsBusy;
            
        return ResponseEntity.ok( 
                    Map.ofEntries(
                        Map.entry( "AutoStart", bAutoStart),
                        Map.entry( "AutoPause", bAutoPause),
                        Map.entry( "AutoStop",      bDeviceAllModulesConnected & !deviceIsBusy),
                        
                        Map.entry( "ManualStart",   bDeviceAllModulesConnected & !deviceIsBusy),
                        Map.entry( "ManualPause",   bDeviceAllModulesConnected),
                        Map.entry( "ManualStop",    bDeviceAllModulesConnected),
                        Map.entry( "ManualOpenShield",  bDeviceAllModulesConnected),
                        Map.entry( "ManualCloseShield", bDeviceAllModulesConnected)
                    )
        );
    }
}
