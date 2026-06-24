package com.tav.userservice.event;

import com.tav.userservice.service.KeycloakUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * UserCreatedEvent'i transaction COMMIT'ten sonra dinler.
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT) garantisi:
 *   - DB transaction commit olmadan bu metod çalışmaz.
 *   - DB connection, Keycloak HTTP çağrısından önce pool'a geri döner.
 *   - Keycloak başarısız olursa DB rollback edilemez; hata loglanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakSyncEventListener {

    private final KeycloakUserSyncService keycloakUserSyncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        try {
            keycloakUserSyncService.createUser(
                    event.username(),
                    event.email(),
                    event.password(),
                    event.roles()
            );
        } catch (Exception ex) {
            // DB commit oldu; Keycloak senkronizasyonu başarısız.
            // Kullanıcı local DB'de var ama Keycloak'ta yok — manuel müdahale gerekebilir.
            log.error("Keycloak senkronizasyonu başarısız — kullanıcı={}, hata={}",
                    event.username(), ex.getMessage(), ex);
        }
    }
}
