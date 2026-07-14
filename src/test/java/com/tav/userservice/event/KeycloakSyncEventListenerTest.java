package com.tav.userservice.event;

import com.tav.userservice.entity.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * KeycloakSyncEventListener sorumlulukları:
 *   1. PendingPasswordStore'dan şifreyi al.
 *   2. Şifre null ise → executor'ı ÇAĞIRMA.
 *   3. Şifre varsa → executor.syncWithRetry() çağır (proxy üzerinden).
 *
 * Retry + activate mantığı KeycloakSyncExecutorTest'te test edilir.
 */
@DisplayName("KeycloakSyncEventListener — listener yönlendirme testleri")
@ExtendWith(MockitoExtension.class)
class KeycloakSyncEventListenerTest {

    @Mock
    private PendingPasswordStore pendingPasswordStore;

    @Mock
    private KeycloakSyncExecutor keycloakSyncExecutor;

    @InjectMocks
    private KeycloakSyncEventListener listener;

    private UserCreatedEvent buildEvent(UUID eventId) {
        return new UserCreatedEvent(eventId, 42L, "alice", "alice@example.com",
                Set.of(RoleName.OPERATION_OFFICER));
    }

    @Test
    @DisplayName("Şifre varsa executor.syncWithRetry çağrılır")
    void onUserCreated_passwordPresent_callsExecutor() {
        UUID eventId = UUID.randomUUID();
        when(pendingPasswordStore.consume(eventId)).thenReturn("s3cret");

        listener.onUserCreated(buildEvent(eventId));

        verify(keycloakSyncExecutor).syncWithRetry(any(UserCreatedEvent.class), eq("s3cret"));
    }

    @Test
    @DisplayName("Şifre TTL dolmuşsa executor hiç çağrılmaz")
    void onUserCreated_passwordExpired_neverCallsExecutor() {
        UUID eventId = UUID.randomUUID();
        when(pendingPasswordStore.consume(eventId)).thenReturn(null);

        listener.onUserCreated(buildEvent(eventId));

        verifyNoInteractions(keycloakSyncExecutor);
    }
}
