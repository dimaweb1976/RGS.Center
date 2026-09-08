package com.rgs.center.onlyone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlyOneLockServiceTests {

    private static final String TEST_PASSWORD = "test-master-password";

    private OnlyOneLockRepository repository;
    private OnlyOneLockService service;

    @BeforeEach
    void setUp() {
        repository = mock(OnlyOneLockRepository.class);
        var passwordEncoder = new BCryptPasswordEncoder();
        var verifier = new MasterPasswordVerifier(passwordEncoder, passwordEncoder.encode(
                TEST_PASSWORD
        ));
        service = new OnlyOneLockService(repository, verifier, 5);
    }

    @Test
    void enterCreatesUuidTokenAndReturnsDatabaseExpiry() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 8, 10, 15);
        when(repository.tryEnter(anyString(), anyString(), eq(5)))
                .thenReturn(Optional.of(expiresAt));

        EnterResult result = service.enter(TEST_PASSWORD, "192.168.1.10");

        assertThat(UUID.fromString(result.token())).isNotNull();
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        verify(repository).tryEnter(result.token(), "192.168.1.10", 5);
    }

    @Test
    void enterRejectsInvalidMasterPasswordWithoutTouchingDatabase() {
        assertThatThrownBy(() -> service.enter("wrong", "192.168.1.10"))
                .isInstanceOf(InvalidMasterPasswordException.class);
    }

    @Test
    void enterReportsBusyWhenAtomicUpdateChangesNoRows() {
        when(repository.tryEnter(anyString(), anyString(), eq(5)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enter(TEST_PASSWORD, "192.168.1.10"))
                .isInstanceOf(SystemBusyException.class);
    }

    @Test
    void checkBusyHidesExpiredLeaseDetails() {
        when(repository.readState()).thenReturn(new OnlyOneLockState(
                "expired-token",
                "192.168.1.10",
                LocalDateTime.of(2026, 9, 8, 10, 15),
                false
        ));

        OnlyOneLockState state = service.checkBusy();

        assertThat(state.busy()).isFalse();
        assertThat(state.token()).isNull();
        assertThat(state.clientIp()).isNull();
        assertThat(state.expiresAt()).isNull();
    }
}
