package com.rgs.center.gammapro;

import com.rgs.center.peripheral.PeripheralWorkerState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GammaProConnectionStatusTests {

    @Test
    void reportsConnectedOnlyForRunningWorker() {
        for (PeripheralWorkerState state : PeripheralWorkerState.values()) {
            GammaProConnectionStatus expected =
                    state == PeripheralWorkerState.RUNNING
                            ? GammaProConnectionStatus.CONNECTED
                            : GammaProConnectionStatus.DISCONNECTED;

            assertEquals(expected, GammaProConnectionStatus.from(state));
        }
    }
}
