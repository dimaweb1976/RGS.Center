package com.rgs.center.restapi;

import com.rgs.center.dobot.ServiceTcpDobotStreamLineConnection;
import com.rgs.center.gammapro.ServiceTcpGammaProConnection;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class GetData4ViewDiagnostics {
    
    @Autowired
    ServiceTcpDobotStreamLineConnection serviceDobot;
    
    @Autowired
    ServiceTcpGammaProConnection serviceGammaPro;
    
    public GetData4ViewDiagnostics() {
    }
    
    @GetMapping("/v1/diagnostics")
    public ResponseEntity<?> aboutLines() {
        String strPlcStatus = "UNKNOWN";
        strPlcStatus = serviceDobot.isActive()? "ONLINE" : "OFFLINE";
        
        String strGammaProStatus = "UNKNOWN";
        strGammaProStatus = serviceGammaPro.isActive()? "ONLINE" : "OFFLINE";
        
        String strShieldStatus = "UNKNOWN";
        
        String strDetectorStatus = "UNKNOWN";
        
        String strMcaStatus = "UNKNOWN";
        
        String strCoolerStatus = "UNKNOWN";
        
        String strDetectorTemperature = "-170";
        /*try {
            strDetectorTemperature = String.format( "%.2f", spshot.equipment().shp().detectorTemperatureCelsius());
        }
        catch( Exception ex) {
            
        }*/
        
        boolean b = Math.random() > 0.5 ? true : false;
        
        return ResponseEntity.ok( 
                Map.ofEntries(
                    Map.entry( "plc", "OFFLINE"),
                    Map.entry( "gripper", "Online"),
                    Map.entry( "axis", "Online"),
                    Map.entry( "gammapro", "OFFLINE"),
                    Map.entry( "shield", "Unknown"),
                    Map.entry( "detector_hv", "HV OFF"),
                    Map.entry( "detector_leakage", "-"),
                    Map.entry( "detector_temperature", strDetectorTemperature),                    
                    Map.entry( "TurnHvOn", b),
                    Map.entry( "TurnHvOff", !b),
                    Map.entry( "mca", "Unknown"),
                    Map.entry( "cooling_system", "Unknown"),
                    Map.entry( "ln2_lev", "" + ( 50 + ( int) (Math.random()*25.))),
                    Map.entry( "ups", "" + ( 90 + ( int) (Math.random()*10.))),
                    Map.entry( "camera1", "Online"),
                    Map.entry( "camera2", "Online"),
                    Map.entry( "scales", "Online"),
                    Map.entry( "scanner", "Online")
                )
            );
    }
}
