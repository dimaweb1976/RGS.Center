package com.rgs.center.gammapro;

import com.rgs.center.peripheral.PeripheralWorkerState;

/** Фактическое состояние TCP-сокета соединения с Gamma-PRO. */
public enum GammaProConnectionStatus {
    CONNECTED,
    DISCONNECTED;

    /**
     * Worker переходит в RUNNING только после успешного открытия транспорта.
     * Все остальные состояния означают, что рабочий сокет отсутствует.
     */
    public static GammaProConnectionStatus from(
            PeripheralWorkerState workerState
    ) {
        return workerState == PeripheralWorkerState.RUNNING
                ? CONNECTED
                : DISCONNECTED;
    }
}
