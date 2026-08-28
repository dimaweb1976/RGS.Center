package com.rgs.center.nicole;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Рассчитывает техническую готовность независимо от доступности TCP-связи. */
@Component
public class NicoleTechnicalStatusEvaluator {

    /**
     * @param snapshot последний корректный PONG
     * @return READY только при работающем охладителе и нулевых error/warning
     */
    public NicoleReadinessStatus evaluate(NicoleSnapshot snapshot) {
        if (snapshot == null) {
            return NicoleReadinessStatus.NO_DATA;
        }
        return snapshot.cryocoolerStarted()
                && snapshot.coolerErrorCode() == 0
                && snapshot.warningFlags() == 0
                ? NicoleReadinessStatus.READY
                : NicoleReadinessStatus.ALERT;
    }

    /** @return человекочитаемые причины ALERT для журнала и сценария */
    public List<String> alertReasons(NicoleSnapshot snapshot) {
        if (snapshot == null) {
            return List.of("No valid NICOLE PONG data");
        }
        List<String> reasons = new ArrayList<>();
        if (!snapshot.cryocoolerStarted()) {
            reasons.add("Cryocooler stopped");
        }
        if (snapshot.coolerErrorCode() != 0) {
            reasons.add("Cooler error code: " + snapshot.coolerErrorCode());
        }
        reasons.addAll(NicoleWarningDecoder.decode(snapshot.warningFlags()));
        return List.copyOf(reasons);
    }
}
