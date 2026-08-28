package com.rgs.center.nicole;

/**
 * Модель таблицы состояния NICOLE.
 *
 * @param snapshot последний корректный PONG либо {@code null}
 * @param warningText расшифрованная битовая маска предупреждений
 * @param connectionStatus доступность по протокольному PING/PONG
 * @param readiness техническое состояние охладителя
 */
public record NicoleStatusView(
        NicoleSnapshot snapshot,
        String warningText,
        NicoleConnectionStatus connectionStatus,
        NicoleReadinessStatus readiness
) {
    /** Создаёт web-модель с независимыми состояниями связи и оборудования. */
    public static NicoleStatusView of(
            NicoleSnapshot snapshot,
            NicoleConnectionStatus connectionStatus,
            NicoleReadinessStatus readiness
    ) {
        return new NicoleStatusView(
                snapshot,
                snapshot == null
                        ? "-"
                        : NicoleWarningDecoder.describe(snapshot.warningFlags()),
                connectionStatus,
                readiness
        );
    }
}
