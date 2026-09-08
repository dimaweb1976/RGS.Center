package com.rgs.center.onlyone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlyOneControllerTests {

    private OnlyOneLockService service;
    private OnlyOneController controller;

    @BeforeEach
    void setUp() {
        service = mock(OnlyOneLockService.class);
        controller = new OnlyOneController(service);
    }

    @Test
    void enterUsesFirstForwardedAddress() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 8, 10, 15);
        when(service.enter("masterpass", "192.168.1.10"))
                .thenReturn(new EnterResult("new-token", expiresAt));
        var request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.168.1.10, 10.0.0.1");

        var response = controller.enter(new EnterRequest("masterpass"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(
                new EnterResponse("new-token", expiresAt)
        );
    }

    @Test
    void competingEnterReturnsLocked() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        when(service.enter("masterpass", "127.0.0.1"))
                .thenThrow(new SystemBusyException());

        var response = controller.enter(new EnterRequest("masterpass"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "System is busy"));
    }

    @Test
    void updateBusyRequiresHeaderAndRejectsExpiredToken() {
        assertThat(controller.updateBusy(null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        when(service.updateBusy("expired-token")).thenReturn(Optional.empty());

        assertThat(controller.updateBusy("expired-token").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void exitIsIdempotentForProvidedToken() {
        var response = controller.exit("already-released-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).exit("already-released-token");
    }

    @Test
    void checkBusyReturnsOnlyPublicLeaseFields() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 8, 10, 15);
        when(service.checkBusy()).thenReturn(new OnlyOneLockState(
                "private-token",
                "192.168.1.10",
                expiresAt,
                true
        ));

        BusyResponse response = controller.checkBusy();

        assertThat(response).isEqualTo(
                new BusyResponse(true, expiresAt, "192.168.1.10")
        );
    }
}
