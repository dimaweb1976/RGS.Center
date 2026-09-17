package com.rgs.center;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CenterWarehouse {
    private final ConcurrentHashMap <String, Object> warehouse;
    
    /**
     * possible values for key="isRunning" warehouse unit
     */
    public enum RgsDeviceRunState {
        RGS_CENTER_DEVICE_RUN_UNKNOWN,
        RGS_CENTER_DEVICE_RUN_IDLE,
        RGS_CENTER_DEVICE_RUN_IN_AUTOMODE,
        RGS_CENTER_DEVICE_RUN_IN_MANUALMODE,
        RGS_CENTER_DEVICE_RUN_IN_BG_ACQ
    };
    
    public CenterWarehouse() {
        warehouse = new ConcurrentHashMap();
        
        warehouse.put( "isRunning", RgsDeviceRunState.RGS_CENTER_DEVICE_RUN_IDLE);
    }
    
    public Object getWareHouseItem( String key) {
        return warehouse.get( key);
    }
    
    public void  putWareHouseItem( String key, Object value) {
        warehouse.put( key, value);
    }
}
