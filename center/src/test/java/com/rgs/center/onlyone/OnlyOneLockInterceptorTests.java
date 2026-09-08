package com.rgs.center.onlyone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OnlyOneLockInterceptorTests {

    private OnlyOneLockService service;
    private OnlyOneLockInterceptor interceptor;

    @BeforeEach
    void setUp() {
        service = mock(OnlyOneLockService.class);
        interceptor = new OnlyOneLockInterceptor(service);
    }

    @Test
    void readOnlyRequestDoesNotRequireLease() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/devices");
        var response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    void stateChangingRequestRequiresHeader() throws Exception {
        var request = new MockHttpServletRequest("POST", "/measurement-emulator/start");
        var response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(423);
        assertThat(response.getContentAsString())
                .contains("Exclusive access lock is required");
    }

    @Test
    void activeTokenAllowsStateChangingRequest() throws Exception {
        var request = new MockHttpServletRequest("POST", "/measurement-emulator/start");
        request.addHeader(OnlyOneController.LOCK_TOKEN_HEADER, "active-token");
        var response = new MockHttpServletResponse();
        when(service.checkMe("active-token")).thenReturn(Optional.of(
                LocalDateTime.of(2026, 9, 8, 10, 15)
        ));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
