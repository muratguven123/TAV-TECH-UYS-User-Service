package com.tav.userservice.event;

import com.tav.userservice.repository.UserRepository;
import com.tav.userservice.service.KeycloakUserSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEF-001 FIX: Keycloak sync retry + activate mantığı ayrı bir bean'e taşındı.
 *
 * Neden ayrı sınıf:
 *   @Retryable ve @Transactional, Spring'in AOP proxy'si üzerinden çalışır.
 *   KeycloakSyncEventListener içinden this.syncWithRetry() çağrılsaydı proxy bypass
 *   olur, retry ve transaction hiçbir zaman devreye girmezdi.
 *   Bu bean ayrı bir Spring proxy'si olduğundan her iki annotation da düzgün çalışır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakSyncExecutor {

    private final KeycloakUserSyncService keycloakUserSyncService;
    private final UserRepository userRepository;

    /**
     * DEF-001: Spring Retry ile maksimum 3 deneme, exponential backoff.
     * 1. deneme hemen, 2. deneme 2s sonra, 3. deneme 4s sonra.
     * Tüm denemeler başarısız olursa recover() devreye girer.
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncWithRetry(UserCreatedEvent event, String password) {
        log.info("Keycloak sync deneniyor: userId={}, username={}", event.userId(), event.username());
        keycloakUserSyncService.createUser(
                event.username(), event.email(), password, event.roles()
        );
        // DEF-001: Keycloak başarılı → kullanıcıyı aktif et
        int updated = userRepository.activateUser(event.userId());
        if (updated == 0) {
            log.warn("DEF-001: activateUser etkilenen satır yok — userId={}", event.userId());
        } else {
            log.info("DEF-001: Kullanıcı aktif edildi: userId={}, username={}", event.userId(), event.username());
        }
    }

    /**
     * DEF-001: Tüm retry'lar tükendikten sonra çağrılır.
     * Kullanıcı DB'de is_active=false olarak kalır — sisteme giremez.
     * Ops ekibi log'u görüp manuel müdahale yapmalı.
     */
    @Recover
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverSync(Exception ex, UserCreatedEvent event, String password) {
        log.error("DEF-001 CRITICAL: Keycloak sync 3 retry sonrası başarısız — " +
                  "kullanıcı DB'de disabled kalıyor. userId={}, username={}, error={}",
                  event.userId(), event.username(), ex.getMessage(), ex);
        // Kullanıcı is_active=false olarak kalır.
        // İleride: bu durumu dead-letter tablosuna veya alerting sistemine yaz.
    }
}
