package com.rgs.center.restapi;

import com.rgs.center.gammapro.ServiceTcpGammaProConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class RestApiStartBgMeas {
    
    @Autowired
    ServiceTcpGammaProConnection serviceGammaPro;
    
    public RestApiStartBgMeas() {
    }
    
    @GetMapping("/v1/startBgMeas")
    public ResponseEntity<?> aboutLines() {
        
        //SET_TRACT(BOSON,TEST)
        //SET_PRESET(3600,1)
        //SET_SPC_FILE_TRACT(C:\GammaPRO\bkg\background.asw)
        //CLEAR_BUFF(BOSON,TEST)
        //START(BOSON,TEST)
        //WAIT_MEAS_END(BOSON,TEST)
        
        


        serviceGammaPro.AddCommandToSend( "SET_TRACT(TEST,1)\r\n".getBytes());
        //serviceGammaPro.AddCommandToSend( "SET_HV(1,1050,0)\r\n".getBytes());
        serviceGammaPro.AddCommandToSend( "SET_PRESET(10,1)\r\n".getBytes());
        serviceGammaPro.AddCommandToSend( "SET_SPC_FILE_TRACT(C:\\GammaPRO\\bkg\\background.asw)\r\n".getBytes());
        serviceGammaPro.AddCommandToSend( "CLEAR_BUFF(TEST,1)\r\n".getBytes());
        serviceGammaPro.AddCommandToSend( "START(TEST,1)\r\n".getBytes());
        serviceGammaPro.AddCommandToSend( "WAIT_MEAS_END(TEST,1)\r\n".getBytes());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd.HH.mm");
        String timestamp = LocalDateTime.now().format(formatter);
        String strCmd = String.format( "SAVE_CUR_SPC_FILE(c:\\gammaPro\\bkg-test\\bg.acq.%s.asw)\r\n", timestamp);
        serviceGammaPro.AddCommandToSend( strCmd.getBytes());
                
        return ResponseEntity.ok( null);
    }
}
