package com.rgs.center;

import com.rgs.center.restapi.ActionRunButtonsState;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CenterWarehouse {
    private final ConcurrentHashMap <String, Object> warehouse;
    
    private static final Logger log = LoggerFactory.getLogger( ActionRunButtonsState.class);
    
    /**
     * possible values for key="isRunning" warehouse unit
     */
    public enum RgsDeviceRunState {
        RGS_CENTER_DEVICE_RUN_UNKNOWN,
        RGS_CENTER_DEVICE_RUN_IDLE,
        RGS_CENTER_DEVICE_RUN_IN_AUTOMODE,
        RGS_CENTER_DEVICE_RUN_IN_AUTOMODE_PAUSE,
        RGS_CENTER_DEVICE_RUN_IN_MANUALMODE,
        RGS_CENTER_DEVICE_RUN_IN_MANUALMODE_PAUSE,
        RGS_CENTER_DEVICE_RUN_IN_BG_ACQ,
        RGS_CENTER_DEVICE_RUN_IN_BG_ACQ_PAUSE,
        RGS_CENTER_DEVICE_RUN_IN_FAULT
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
