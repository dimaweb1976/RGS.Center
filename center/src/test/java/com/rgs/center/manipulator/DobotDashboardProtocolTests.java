package com.rgs.center.manipulator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DobotDashboardProtocolTests {

    private final DobotDashboardProtocol protocol = new DobotDashboardProtocol();

    @Test
    void encodesCommandAndParsesMovementResultId() {
        assertEquals(
                "GetPose()",
                new String(protocol.encode("GetPose()"), StandardCharsets.US_ASCII)
        );
        DobotCommandResponse response = protocol.parse(
                "0,{77},MovJ(1,2,3,4,5,6);".getBytes(StandardCharsets.US_ASCII)
        );
        assertTrue(response.successful());
        assertEquals(77L, response.firstLongValue().orElseThrow());
    }

    @Test
    void rejectsUnsafeOrMalformedFrames() {
        assertThrows(IllegalArgumentException.class, () -> protocol.encode("GetPose()\n"));
        assertThrows(
                RuntimeException.class,
                () -> protocol.parse("not-a-response;".getBytes(StandardCharsets.US_ASCII))
        );
    }
}
