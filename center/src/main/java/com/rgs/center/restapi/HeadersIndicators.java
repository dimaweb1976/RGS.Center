package com.rgs.center.restapi;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.GammaProEquipmentSnapshot;
import java.time.Instant;
import com.rgs.center.dashboard.model.NicoleEquipmentSnapshot;
import com.rgs.center.dashboard.model.TechnicalState;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/")
public class HeadersIndicators {
    
    private final DashboardSnapshotService dashboardSnapshotService;
    
    public HeadersIndicators( DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }
    @GetMapping("/v1/indicators")
    public ResponseEntity<?> aboutLines() {
        
        DashboardSnapshot spshot = dashboardSnapshotService.snapshot();
                
        double d = Math.random() * 3.;
        String strShieldStatus = "UNDEFINED";
        if( d > 2.)
            strShieldStatus = "OPEN";
        else if( d > 1.)    
            strShieldStatus = "CLOSED";
        
        
        d = Math.random() * 3.;
        String strPlcStatus = "ONLINE";
        if( d > 2.)
            strPlcStatus = "FAULT";
        else if( d > 1.)    
            strPlcStatus = "IN PROCESS";

        return ResponseEntity.ok( 
                Map.of(
                    "plc", "UNKNOWN", //resolveStatus( spshot.equipment().manipulator().health()),
                    //"gammapro", strShieldStatus, //resolveStatus( spshot.equipment().gammaPro().health()),
                    "gammapro", resolveGammaProStatus(spshot.equipment().gammaPro()),
                    "shield", "UNKNOWN",
                    "detector", "UNKNOWN", //resolveStatus( spshot.equipment().shp().health()),
                    "MCA", "UNKNOWN", //"UNDEFINED",
                    //"cooler", strShieldStatus //resolveStatus( spshot.equipment().nicole().health())
                    "cooler", resolveStatus( spshot.equipment().nicole().health())
                )
            );
    }
    
    
    /** Индикатор программы GammaPRO, независимо от физического BOSON/HV. */
    private static String resolveGammaProStatus(GammaProEquipmentSnapshot gammaPro) {
        return resolveGammaProStatus(gammaPro, Instant.now());
    }

    /** Отдельное время позволяет детерминированно проверять срок операции. */
    static String resolveGammaProStatus(GammaProEquipmentSnapshot gammaPro, Instant now) {
        if (gammaPro == null || !gammaPro.health().enabled()
                || !gammaPro.connectionAttempted()) {
            return "UNKNOWN";
        }
        var connection = gammaPro.health().connection();
        if (connection == ConnectionState.OFFLINE) {
            return "OFFLINE";
        }
        if (connection != ConnectionState.ONLINE || gammaPro.responseUncertain()) {
            return "UNKNOWN";
        }
        if (gammaPro.businessCommandActive()) {
            return gammaPro.heartbeatFresh() ? "IN_PROGRESS" : "UNKNOWN";
        }
        if (gammaPro.acquisitionState()
                == GammaProEquipmentSnapshot.SpectrumAcquisitionState.RUNNING) {
            // RUNNING устанавливается только после START=OK. Во время
            // WAIT_MEAS_END не требуем idle-heartbeat, но ограничиваем срок.
            return gammaPro.measurementDeadlineAt() != null
                    && now.isBefore(gammaPro.measurementDeadlineAt())
                    ? "IN_PROGRESS" : "UNKNOWN";
        }
        return gammaPro.heartbeatFresh() ? "ONLINE" : "UNKNOWN";
    }

    private static String resolveStatus( DeviceHealth health) {
        if( !health.enabled() || health.connection() == ConnectionState.DISABLED)
            return "DISABLED";
        if( health.connection() == ConnectionState.OFFLINE)
            return "OFFLINE";
        if( health.stale())
            return "STALE";
        if( health.technical() == TechnicalState.ALERT)
            return "ALERT";
        if( health.technical() == TechnicalState.NO_DATA)
            return "NO_DATA";
        if( health.technical() == TechnicalState.READY)
            return "READY";
        return health.technical().name();
    }
}
