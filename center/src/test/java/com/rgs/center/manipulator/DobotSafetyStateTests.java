package com.rgs.center.manipulator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DobotSafetyStateTests {

    @Test
    void decodesDocumentedActiveLowBits() {
        DobotSafetyState safe = DobotSafetyState.decode(0xFF);
        DobotSafetyState stopped = DobotSafetyState.decode(0x7C);

        assertThat(safe.blockingFaults()).isEmpty();
        assertThat(stopped.emergencyStop()).isTrue();
        assertThat(stopped.protectiveStop()).isTrue();
        assertThat(stopped.blockingFaults()).containsExactly(
                "Emergency stop",
                "Protective stop"
        );
    }
}
