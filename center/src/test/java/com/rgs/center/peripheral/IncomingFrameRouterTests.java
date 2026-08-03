package com.rgs.center.peripheral;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IncomingFrameRouterTests {

    @Test
    void keepsIndependentQueuesAndDropsOnlyOldestOnSameRoute() {
        IncomingFrameRouter router = new IncomingFrameRouter(
                (source, frame) -> source.equals("telemetry")
                        ? IncomingFrameRoute.TELEMETRY
                        : IncomingFrameRoute.COMMAND_RESPONSE,
                1
        );

        router.route("command", new byte[]{1});
        router.route("telemetry", new byte[]{2});
        router.route("telemetry", new byte[]{3});

        assertArrayEquals(
                new byte[]{1},
                router.await(IncomingFrameRoute.COMMAND_RESPONSE, Duration.ofMillis(10))
                        .orElseThrow().payload()
        );
        assertArrayEquals(
                new byte[]{3},
                router.await(IncomingFrameRoute.TELEMETRY, Duration.ofMillis(10))
                        .orElseThrow().payload()
        );
        assertEquals(
                1L,
                router.snapshot().droppedFrames().get(IncomingFrameRoute.TELEMETRY)
        );
    }
}
