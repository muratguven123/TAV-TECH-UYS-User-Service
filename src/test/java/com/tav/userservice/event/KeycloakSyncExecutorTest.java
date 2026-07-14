package com.tav.userservice.event;

import com.tav.userservice.entity.RoleName;
import com.tav.userservice.repository.UserRepository;
import com.tav.userservice.service.KeycloakUserSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * KeycloakSyncExecutor sorumlulukları:
 *   1. Keycloak başarılı → activateUser çağrılır.
 *   2. Keycloak exception → exception propagate edilir (retry proxy'si yakalar).
 *   3. recoverSync → activateUser çağrılmaz, CRITICAL log atılır.
 *
 * NOT: @Retryable Spring proxy'si MockitoExtension'da aktif değildir.
 * Retry davranışının tam testi için @SpringBootTest + WireMock gerekir.
 */
@DisplayName("KeycloakSyncExecutor — retry/activate mantık testleri")
@ExtendWith(MockitoExtension.class)
class KeycloakSyncExecutorTest {

    @Mock
    private KeycloakUserSyncService keycloakUserSyncService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KeycloakSyncExecutor executor;

    private UserCreatedEvent buildEvent() {
        return new UserCreatedEvent(UUID.randomUUID(), 42L, "alice",
                "alice@example.com", Set.of(RoleName.OPERATION_OFFICER));
    }

    @Test
    @DisplayName("syncWithRetry: Keycloak başarılı → activateUser çağrılır")
    void syncWithRetry_keycloakSuccess_activatesUser() {
        when(userRepository.activateUser(42L)).thenReturn(1);

        executor.syncWithRetry(buildEvent(), "pw");

        verify(keycloakUserSyncService).createUser("alice", "alice@example.com",
                "pw", Set.of(RoleName.OPERATION_OFFICER));
        verify(userRepository).activateUser(42L);
    }

    @Test
    @DisplayName("syncWithRetry: Keycloak exception fırlatırsa propagate eder (retry proxy yakalar)")
    void syncWithRetry_keycloakThrows_propagatesException() {
        doThrow(new RuntimeException("Keycloak down"))
                .when(keycloakUserSyncService)
                .createUser(any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> executor.syncWithRetry(buildEvent(), "pw"));

        // Keycloak başarısız → activateUser çağrılmamalı
        verify(userRepository, never()).activateUser(any());
    }

    @Test
    @DisplayName("recoverSync: tüm retry'lar tükenince activateUser çağrılmaz")
    void recoverSync_doesNotActivateUser() {
        executor.recoverSync(new RuntimeException("final failure"), buildEvent(), "pw");

        verifyNoInteractions(userRepository);
    }
}
