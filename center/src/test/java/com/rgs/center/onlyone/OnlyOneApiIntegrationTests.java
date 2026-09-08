package com.rgs.center.onlyone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the exclusive-access HTTP contract without a browser or frontend.
 * Service behavior is covered separately by {@link OnlyOneLockServiceTests}.
 */
@SpringBootTest(properties = {
        "rgs.device.camera.enabled=false",
        "rgs.device.lenovo-pc.enabled=false",
        "rgs.device.nicole.enabled=false",
        "rgs.device.boson.enabled=false",
        "rgs.device.manipulator.enabled=false",
        "rgs.device.barcode-scanner.enabled=false",
        "rgs.device.gdrm.enabled=false",
        "rgs.device.scales.enabled=false",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false",
        "rgs.only-one.enforce=false"
})
@AutoConfigureMockMvc
class OnlyOneApiIntegrationTests {

    private static final LocalDateTime EXPIRES_AT =
            LocalDateTime.of(2026, 9, 8, 10, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnlyOneLockService lockService;

    @BeforeEach
    void resetService() {
        reset(lockService);
    }

    @Test
    void enterReturnsTokenAndUsesFirstForwardedClientAddress() throws Exception {
        when(lockService.enter("masterpass", "192.168.1.10"))
                .thenReturn(new EnterResult("new-token", EXPIRES_AT));

        mockMvc.perform(post("/api/onlyOne/enter")
                        .header("X-Forwarded-For", "192.168.1.10, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"masterpass\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.token").value("new-token"))
                .andExpect(jsonPath("$.expiresAt")
                        .value("2026-09-08T10:15:00"));

        verify(lockService).enter("masterpass", "192.168.1.10");
    }

    @Test
    void enterReturnsUnauthorizedForInvalidPassword() throws Exception {
        when(lockService.enter(anyString(), anyString()))
                .thenThrow(new InvalidMasterPasswordException());

        mockMvc.perform(post("/api/onlyOne/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid password"));
    }

    @Test
    void enterReturnsLockedWhenAnotherClientOwnsTheLease() throws Exception {
        when(lockService.enter(anyString(), anyString()))
                .thenThrow(new SystemBusyException());

        mockMvc.perform(post("/api/onlyOne/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"masterpass\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("System is busy"));
    }

    @Test
    void enterRejectsMalformedJsonBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/onlyOne/enter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(lockService);
    }

    @Test
    void checkBusyReturnsActiveLeaseWithoutPrivateToken() throws Exception {
        when(lockService.checkBusy()).thenReturn(new OnlyOneLockState(
                "private-token",
                "192.168.1.10",
                EXPIRES_AT,
                true
        ));

        mockMvc.perform(get("/api/onlyOne/checkBusy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busy").value(true))
                .andExpect(jsonPath("$.busyUntil")
                        .value("2026-09-08T10:15:00"))
                .andExpect(jsonPath("$.clientIp").value("192.168.1.10"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void checkBusyReturnsEmptyFieldsForAFreeSystem() throws Exception {
        when(lockService.checkBusy()).thenReturn(
                new OnlyOneLockState(null, null, null, false)
        );

        mockMvc.perform(get("/api/onlyOne/checkBusy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busy").value(false))
                .andExpect(jsonPath("$.busyUntil").isEmpty())
                .andExpect(jsonPath("$.clientIp").isEmpty());
    }

    @Test
    void checkMeValidatesTheRequiredLockTokenHeader() throws Exception {
        mockMvc.perform(get("/api/onlyOne/checkMe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("X-Lock-Token header is required"));

        when(lockService.checkMe("expired-token")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/onlyOne/checkMe")
                        .header("X-Lock-Token", "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.active").value(false));

        when(lockService.checkMe("active-token"))
                .thenReturn(Optional.of(EXPIRES_AT));
        mockMvc.perform(get("/api/onlyOne/checkMe")
                        .header("X-Lock-Token", "active-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.expiresAt")
                        .value("2026-09-08T10:15:00"));
    }

    @Test
    void updateBusyValidatesAndExtendsTheCurrentLease() throws Exception {
        mockMvc.perform(post("/api/onlyOne/updateBusy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("X-Lock-Token header is required"));

        when(lockService.updateBusy("expired-token"))
                .thenReturn(Optional.empty());
        mockMvc.perform(post("/api/onlyOne/updateBusy")
                        .header("X-Lock-Token", "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error")
                        .value("Invalid or expired lock token"));

        when(lockService.updateBusy("active-token"))
                .thenReturn(Optional.of(EXPIRES_AT));
        mockMvc.perform(post("/api/onlyOne/updateBusy")
                        .header("X-Lock-Token", "active-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("extended"))
                .andExpect(jsonPath("$.expiresAt")
                        .value("2026-09-08T10:15:00"));
    }

    @Test
    void exitRequiresAHeaderAndIsIdempotentForProvidedTokens() throws Exception {
        mockMvc.perform(post("/api/onlyOne/exit"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("X-Lock-Token header is required"));

        mockMvc.perform(post("/api/onlyOne/exit")
                        .header("X-Lock-Token", "already-released-token"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(lockService).exit("already-released-token");
    }
}
