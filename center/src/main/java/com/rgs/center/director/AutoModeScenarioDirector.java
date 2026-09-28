package com.rgs.center.director;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

public class AutoModeScenarioDirector {
    
    @Autowired
    RGSCenterWarehouse warehouse;
    
    private AutoModeScenarioDirectorState nState;
    
    static public enum AutoModeScenarioDirectorState {
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_UNKNOWN,
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_IDLE,
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_RUN,
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_PAUSE
    };
    
    public AutoModeScenarioDirector() {
    }
    
    @PostConstruct
    public void autoRunOnStart() {
        nState = AutoModeScenarioDirectorState.RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_IDLE;
        warehouse.putItem( "autoMode.director.run.state", nState);
    }
    
}
