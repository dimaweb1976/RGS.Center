package com.rgs.center.nicole;

import java.time.Instant;
import java.util.List;

/**
 * Однократное событие изменения технического состояния после нового PONG.
 *
 * @param previousStatus предыдущее вычисленное состояние
 * @param currentStatus новое вычисленное состояние
 * @param snapshot PONG, вызвавший изменение
 * @param reasons причины ALERT; для READY список пуст
 * @param occurredAt время обнаружения изменения
 */
public record NicoleTechnicalStatusChangedEvent(
        NicoleReadinessStatus previousStatus,
        NicoleReadinessStatus currentStatus,
        NicoleSnapshot snapshot,
        List<String> reasons,
        Instant occurredAt
) {
    /** @return является ли изменение аварийным переходом READY → ALERT */
    public boolean isReadyToAlert() {
        return previousStatus == NicoleReadinessStatus.READY
                && currentStatus == NicoleReadinessStatus.ALERT;
    }
}
