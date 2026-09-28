package com.rgs.center.warehouse;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RGSCenterWarehouse {
    final ConcurrentHashMap< String, Object> warehouse;
    
    public static enum DeviceComplexModes {
        RGS_COMPLEX_IDLE,
        RGS_COMPLEX_AUTO,
        RGS_COMPLEX_MANUAL,
        RGS_COMPLEX_BG_ACQ,
        RGS_COMPLEX_FAIL
    };
    
    public static final String DEVICES_SHP_AMBIENT_TEMPERATURE = "device.shp.ambient.temperature";
    public static final String DEVICES_SHP_DETECTOR_TEMPERATURE = "device.shp.detector.temperature";
    public static final String DEVICES_SHP_CHARGE_OUTPUT_VOLTAGE = "device.shp.charge.output.voltage";
    public static final String DEVICES_SHP_CURRENT = "device.shp.current";
    public static final String DEVICES_SHP_HW_VERSION = "device.shp.hw.version";
    public static final String DEVICES_SHP_FW_VERSION = "device.shp.fw.version";
    public static final String DEVICES_SHP_DATE_OF_MANUFACTURE = "device.shp.date_of_manufacture";
    public static final String DEVICES_SHP_RESISTANCE = "device.shp.resistance";
    
    public static final String DEVICES_DOBOT_X = "device.dobot.x";
    public static final String DEVICES_DOBOT_Y = "device.dobot.y";
    public static final String DEVICES_DOBOT_Z = "device.dobot.z";
    public static final String DEVICES_DOBOT_GRIPPER = "device.dobot.gripper";
    
    public static final String DEVICES_DOBOT_DIGITAL_INPUTS = "device.dobot.di";
    public static final String DEVICES_DOBOT_DIGITAL_OUTPUTS = "device.dobot.do";

    public static final String DEVICES_SCALES_MASS = "device.scales.mass";
    
    public static final String DEVICES_BARCODE_READER_LAST_CODE = "device.barcode.read";
    
    
    public static final String DEVICES_NICOLE_STATUS = "device.nicole.status";
    public static final String DEVICES_NICOLE_RESET_CAUSE = "device.nicole.reset.cause";
    public static final String DEVICES_NICOLE_ETH_UP = "device.nicole.ethernet.up";
    public static final String DEVICES_NICOLE_DHCP_LEASED = "device.nicole.dhcp.leased";
    public static final String DEVICES_NICOLE_CRYO_COM = "device.nicole.cryo.comm";
    public static final String DEVICES_NICOLE_COOLER_STATUS = "device.nicole.cooler.status";
    public static final String DEVICES_NICOLE_COOLER_STARTED = "device.nicole.cooler.started";
    public static final String DEVICES_NICOLE_COOLER_ADVANCED = "device.nicole.cooler.advanced";
    public static final String DEVICES_NICOLE_N2_LEVEL = "device.nicole.n2.level";
    public static final String DEVICES_NICOLE_N2_PRESSURE = "device.nicole.n2.pressure";
    public static final String DEVICES_NICOLE_VOLTAGE_LOW = "device.nicole.voltage.low";
    public static final String DEVICES_NICOLE_VOLTAGE_HIGH = "device.nicole.voltage.high";
    public static final String DEVICES_NICOLE_FREQ = "device.nicole.frequency";
    public static final String DEVICES_NICOLE_TIME_H = "device.nicole.time.hours";
    public static final String DEVICES_NICOLE_TIME_MIN = "device.nicole.time.minutes";
    public static final String DEVICES_NICOLE_TIME_S = "device.nicole.time.seconds";
    public static final String DEVICES_NICOLE_TIME_D = "device.nicole.date.day";
    public static final String DEVICES_NICOLE_TIME_MON = "device.nicole.date.month";
    public static final String DEVICES_NICOLE_TIME_Y = "device.nicole.date.year";
    public static final String DEVICES_NICOLE_COOLER_ERR_CODE = "device.nicole.cooler.error.code";
    public static final String DEVICES_NICOLE_COOLER_WARNINGS = "device.nicole.cooler.warnings";

    public static final String DEVICES_BOSON_HV_IS_APPLIED = "device.boson.hv.is.applied";
    public static final String DEVICES_BOSON_HV_ACTUAL = "device.boson.hv.actual";
    public static final String DEVICES_BOSON_HV_PRESET = "device.boson.hv.preset";
    public static final String DEVICES_BOSON_MODIFICATION = "device.boson.modification";
    public static final String DEVICES_BOSON_VERSION = "device.boson.version";
    public static final String DEVICES_BOSON_PHYS_MANUFACTURE = "device.boson.phys.manufacture";
    public static final String DEVICES_BOSON_DEVICE_CODE = "device.boson.device.code";
    public static final String DEVICES_BOSON_SERIAL = "device.boson.serial";
    public static final String DEVICES_BOSON_ID = "device.boson.id";
    
    public static final List <String> lstShps = List.of(
                DEVICES_SHP_AMBIENT_TEMPERATURE,
                DEVICES_SHP_DETECTOR_TEMPERATURE,
                DEVICES_SHP_CHARGE_OUTPUT_VOLTAGE,
                DEVICES_SHP_CURRENT,
                DEVICES_SHP_HW_VERSION,
                DEVICES_SHP_FW_VERSION,
                DEVICES_SHP_DATE_OF_MANUFACTURE,
                DEVICES_SHP_RESISTANCE
    );
    
    public RGSCenterWarehouse() {
        warehouse = new ConcurrentHashMap();        
    }
    
    @PostConstruct
    public void init() {
    }
    
    public Object getItem( String key) {
        return warehouse.get( key);
    }
    
    public Object putItem( String key, Object value) {
        return warehouse.put( key, value);
    }
}
