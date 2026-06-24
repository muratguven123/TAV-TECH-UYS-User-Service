package com.tav.userservice.event;

import com.tav.userservice.entity.RoleName;
import com.tav.userservice.service.KeycloakUserSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * KeycloakSyncEventListener davranış testleri.
 *
 * Doğrulanan iki garanti:
 *   1. Happy path → KeycloakUserSyncService.createUser tam olarak event'teki değerlerle çağrılır.
 *   2. Keycloak çağrısı exception fırlatırsa listener bunu yutar (rethrow etmez);
 *      aksi halde Spring'in event publisher zinciri etkilenir ve DB commit olmuş işlem
 *      tutarsız bir hata akışına döner.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakSyncEventListenerTest {

    @Mock
    private KeycloakUserSyncService keycloakUserSyncService;

    @InjectMocks
    private KeycloakSyncEventListener listener;

    @Test
    void onUserCreated_happyPath_delegatesToKeycloakSyncWithEventFields() {
        UserCreatedEvent event = new UserCreatedEvent(
                "alice",
                "alice@example.com",
                "s3cret",
                Set.of(RoleName.OPERATION_OFFICER)
        );

        listener.onUserCreated(event);

        verify(keycloakUserSyncService).createUser(
                "alice",
                "alice@example.com",
                "s3cret",
                Set.of(RoleName.OPERATION_OFFICER)
        );
    }

    @Test
    void onUserCreated_whenKeycloakThrows_swallowsExceptionAndDoesNotRethrow() {
        UserCreatedEvent event = new UserCreatedEvent(
                "bob",
                "bob@example.com",
                "pw",
                Set.of(RoleName.BI_SPECIALIST)
        );
        doThrow(new RuntimeException("Keycloak down"))
                .when(keycloakUserSyncService)
                .createUser("bob", "bob@example.com", "pw", Set.of(RoleName.BI_SPECIALIST));

        assertThatCode(() -> listener.onUserCreated(event)).doesNotThrowAnyException();

        verify(keycloakUserSyncService).createUser("bob", "bob@example.com", "pw", Set.of(RoleName.BI_SPECIALIST));
    }
}
