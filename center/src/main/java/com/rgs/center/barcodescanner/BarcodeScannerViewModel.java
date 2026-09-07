package com.rgs.center.barcodescanner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Values prepared for rendering the barcode scanner page. */
public record BarcodeScannerViewModel(
        String connectionLabel,
        String connectionCssClass,
        String operationLabel,
        String operationCssClass,
        String portName,
        String baudRate,
        String attempt,
        String lastHeartbeat,
        String lastScan,
        String lastBarcode,
        String workerState,
        String error,
        boolean initialized,
        boolean canStart,
        boolean canCancel
) {
    private static final String EMPTY = "—";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static BarcodeScannerViewModel from(
            BarcodeScannerSnapshot snapshot,
            boolean readActive
    ) {
        boolean enabled = snapshot.enabled();
        String connectionLabel = !enabled
                ? "DISABLED"
                : (snapshot.connected() ? "ONLINE" : "OFFLINE");
        String connectionCss = !enabled
                ? "scanner-disabled"
                : (snapshot.connected()
                        ? "scanner-online"
                        : "scanner-offline");
        BarcodeScannerOperationState state = readActive
                ? BarcodeScannerOperationState.SCANNING
                : snapshot.operationState();
        String error = firstNonBlank(
                snapshot.lastScanError(),
                snapshot.lastConnectionError(),
                snapshot.communication() == null
                        ? null
                        : snapshot.communication().lastError()
        );
        return new BarcodeScannerViewModel(
                connectionLabel,
                connectionCss,
                operationLabel(state),
                "scanner-operation-" + state.name().toLowerCase(),
                text(snapshot.portName()),
                snapshot.baudRate() > 0
                        ? Integer.toString(snapshot.baudRate())
                        : EMPTY,
                snapshot.currentAttempt() <= 0
                        ? EMPTY
                        : snapshot.currentAttempt() + " / "
                                + snapshot.maxAttempts(),
                dateTime(snapshot.lastHeartbeatEpochSeconds()),
                dateTime(snapshot.lastScanEpochSeconds()),
                text(snapshot.lastBarcode()),
                snapshot.communication() == null
                        ? EMPTY
                        : snapshot.communication().state().name(),
                error,
                snapshot.initialized(),
                enabled && !readActive,
                readActive
        );
    }

    private static String operationLabel(BarcodeScannerOperationState state) {
        return switch (state) {
            case IDLE -> "ОЖИДАНИЕ";
            case SCANNING -> "СКАНИРОВАНИЕ";
            case SUCCESS -> "СЧИТАНО";
            case TIMEOUT -> "НЕ СЧИТАНО";
            case CANCELLED -> "ОТМЕНЕНО";
            case ERROR -> "ОШИБКА";
        };
    }

    private static String dateTime(long epochSeconds) {
        return epochSeconds <= 0
                ? EMPTY
                : DATE_TIME_FORMAT.format(Instant.ofEpochSecond(epochSeconds));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? EMPTY : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return EMPTY;
    }
}
