package com.rgs.center.dashboard.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Единая часть состояния любого устройства RGSCenter.
 *
 * @param code стабильный машинный идентификатор
 * @param name отображаемое имя
 * @param enabled разрешено ли обслуживание устройства
 * @param connection состояние связи
 * @param technical техническая готовность
 * @param operation состояние прикладной операции
 * @param observedAt время последнего достоверного наблюдения или {@code null}
 * @param stale устарели ли последние данные на момент сборки snapshot
 * @param reasons причины текущего технического состояния
 * @param lastError последняя ошибка или пустая строка
 */
public record DeviceHealth(
        String code,
        String name,
        boolean enabled,
        ConnectionState connection,
        TechnicalState technical,
        OperationalState operation,
        Instant observedAt,
        boolean stale,
        List<String> reasons,
        String lastError
) {
    public DeviceHealth {
        requireText(code, "code");
        requireText(name, "name");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(technical, "technical");
        Objects.requireNonNull(operation, "operation");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        lastError = Objects.requireNonNull(lastError, "lastError");
        validateEnablement(enabled, connection, technical, operation);
    }

    private static void validateEnablement(
            boolean enabled,
            ConnectionState connection,
            TechnicalState technical,
            OperationalState operation
    ) {
        boolean allDisabled = connection == ConnectionState.DISABLED
                && technical == TechnicalState.DISABLED
                && operation == OperationalState.DISABLED;
        boolean anyDisabled = connection == ConnectionState.DISABLED
                || technical == TechnicalState.DISABLED
                || operation == OperationalState.DISABLED;
        if (!enabled && !allDisabled) {
            throw new IllegalArgumentException(
                    "Disabled device must use DISABLED states"
            );
        }
        if (enabled && anyDisabled) {
            throw new IllegalArgumentException(
                    "Enabled device cannot use DISABLED states"
            );
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
