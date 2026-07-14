package com.tav.userservice.event;

import com.tav.userservice.event.KeycloakSyncExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DEF-001 FIX:
 *
 * Kullanıcı is_active=false yaratılır → Keycloak sync → başarılıysa is_active=true.
 *
 * Retry + Transactional mantığı KeycloakSyncExecutor'a taşındı.
 * Nedeni: @Retryable ve @Transactional yalnızca Spring proxy üzerinden çalışır.
 * Aynı sınıf içinde this.syncWithRetry() çağrısı proxy'yi bypass ederdi.
 *
 * DEF-002 FIX: Şifre PendingPasswordStore'dan consume() ile alınır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakSyncEventListener {

    private final PendingPasswordStore pendingPasswordStore;
    private final KeycloakSyncExecutor keycloakSyncExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        String password = pendingPasswordStore.consume(event.eventId());
        if (password == null) {
            log.error("DEF-001: PendingPassword TTL dolmuş veya bulunamadı — " +
                      "Keycloak sync iptal, kullanıcı disabled kalıyor: userId={}, username={}",
                      event.userId(), event.username());
            return;
        }
        // Proxy üzerinden çağrı — @Retryable ve @Transactional aktif
        keycloakSyncExecutor.syncWithRetry(event, password);
    }
}
