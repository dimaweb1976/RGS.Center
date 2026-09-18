package com.rgs.center.director;

public class AutoModeScenarioDirector {
    private AutoModeScenarioDirectorState nState;
    
    public enum AutoModeScenarioDirectorState {
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_UNKNOWN,
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_IDLE,
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_RUN,
        RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_PAUSE
    };
    
    public AutoModeScenarioDirector() {
        nState = AutoModeScenarioDirectorState.RGS_CENTER_AUTOMODE_DIRECTOR_RUNSTATE_IDLE;
    }
    
    
}
