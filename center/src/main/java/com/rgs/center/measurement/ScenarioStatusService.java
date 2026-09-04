package com.rgs.center.measurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Потокобезопасная витрина текущего состояния автоматического сценария.
 *
 * <p>Сервис отделяет состояние сценария от статуса соединения и технического
 * состояния конкретного устройства. Его могут обновлять текущий эмулятор и
 * будущий производственный оркестратор, а web-страницы только читают снимок.</p>
 */
@Service
public class ScenarioStatusService {

    private static final Logger log =
            LoggerFactory.getLogger(ScenarioStatusService.class);

    private final AtomicReference<ScenarioStatus> currentStatus =
            new AtomicReference<>(ScenarioStatus.NORMAL);

    /** @return текущее согласованное состояние сценария */
    public ScenarioStatus currentStatus() {
        return currentStatus.get();
    }

    /**
     * Обновляет состояние и журналирует только реальный переход.
     *
     * @param nextStatus новое состояние сценария
     * @param reason краткая причина перехода для журнала
     */
    public void updateStatus(ScenarioStatus nextStatus, String reason) {
        Objects.requireNonNull(nextStatus, "nextStatus");
        ScenarioStatus previousStatus = currentStatus.getAndSet(nextStatus);
        if (previousStatus != nextStatus) {
            log.info(
                    "Measurement scenario status changed: {} -> {}, reason={}",
                    previousStatus,
                    nextStatus,
                    reason == null || reason.isBlank() ? "not specified" : reason
            );
        }
    }
}
