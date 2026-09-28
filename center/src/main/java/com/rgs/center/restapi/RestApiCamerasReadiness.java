package com.rgs.center.restapi;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class RestApiCamerasReadiness {
    
    private static final Logger log = LoggerFactory.getLogger(RestApiCamerasReadiness.class);
    
    public RestApiCamerasReadiness( ) {
    }
    
    @GetMapping("/api/v1/camerasReady")
    public ResponseEntity<?> buttonsState() {
        boolean bCam1Connected = true;
        boolean bCam2Connected = true;
        //boolean bCam2Connected = spshot.equipment().cameras().get( 1).health().connection().equals( ConnectionState.ONLINE);
            
        return ResponseEntity.ok( 
                    Map.ofEntries(
                        Map.entry( "camerasReady", bCam1Connected & bCam2Connected)
                    )
        );
    }
}
