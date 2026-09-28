package com.rgs.center.restapi;

import com.rgs.center.dobot.ServiceTcpDobotStreamLineConnection;
import com.rgs.center.gammapro.ServiceTcpGammaProConnection;
import com.rgs.center.nicole.ServiceTcpNicoleConnection;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class HeadersIndicators {
    
    @Autowired
    ServiceTcpNicoleConnection nicoleService;
    
    @Autowired
    ServiceTcpDobotStreamLineConnection serviceDobot;
    
    @Autowired
    ServiceTcpGammaProConnection serviceGammaPro;
    
    public HeadersIndicators( ) {
    }
    
    @GetMapping("/v1/indicators")
    public ResponseEntity<?> aboutLines() {
        
        String strPlcStatus = "UNKNOWN";
        strPlcStatus = serviceDobot.isActive() ? "ONLINE" : "OFFLINE";
        
        String strGammaProStatus = "UNKNOWN";
        strGammaProStatus = serviceGammaPro.isActive() ? "ONLINE" : "OFFLINE";
        
        String strShieldStatus = "UNKNOWN";
        
        String strDetectorStatus = "UNKNOWN";
        
        String strMcaStatus = "UNKNOWN";
        
        String strCoolerStatus = nicoleService.isActive() ? "ONLINE" : "OFFLINE";        

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
