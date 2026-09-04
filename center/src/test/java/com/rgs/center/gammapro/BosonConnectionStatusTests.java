package com.rgs.center.gammapro;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BosonConnectionStatusTests {

    private static final Instant NOW = Instant.parse(
            "2026-09-04T10:45:57Z"
    );
    private static final Duration STALE_AFTER = Duration.ofSeconds(30);

    @Test
    void connectsForFreshMatchingTract() {
        assertEquals(
                BosonConnectionStatus.CONNECTED,
                status(
                        List.of("DATA TRACT1(BOSON,Ch1),"),
                        NOW.minusSeconds(10)
                )
        );
    }

    @Test
    void disconnectsWhenTractIsMissingOrResponseIsStale() {
        assertEquals(
                BosonConnectionStatus.DISCONNECTED,
                status(
                        List.of("DATA TRACT1(NICOLE,Ch1),"),
                        NOW.minusSeconds(10)
                )
        );
        assertEquals(
                BosonConnectionStatus.DISCONNECTED,
                status(
                        List.of("DATA TRACT1(BOSON,Ch1),"),
                        NOW.minusSeconds(31)
                )
        );
    }

    private BosonConnectionStatus status(
            List<String> tracts,
            Instant updatedAt
    ) {
        return BosonConnectionStatus.from(
                tracts,
                "BOSON",
                "Ch1",
                updatedAt,
                STALE_AFTER,
                NOW
        );
    }
}
