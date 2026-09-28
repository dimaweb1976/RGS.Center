package com.rgs.center;

import com.rgs.center.director.AutoModeScenarioDirector;
import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка запуска серверного приложения RGS.Center.
 * test gitea push email notification
 * test gitea push email notification. try #2
 * test gitea push email notification. try #3
 */
@SpringBootApplication
@EnableScheduling
public class CenterApplication {

    @Autowired
    RGSCenterWarehouse warehouse;
    
    /**
     * @param args аргументы командной строки Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CenterApplication.class, args);
    }
    
    @PostConstruct
    public void selfInit() {
        warehouse.putItem( "device.run.state", RGSCenterWarehouse.DeviceComplexModes.RGS_COMPLEX_IDLE);
    }
}
