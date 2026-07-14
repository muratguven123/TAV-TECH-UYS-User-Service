package com.tav.userservice.event;

// FIX: DEF-002 — şifreyi event'e koymak yerine bu store üzerinden TTL ile taşır.
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Kısa ömürlü, bellekte şifre tamponu.
 *
 * <p>UserService, şifreyi DB commit'i beklerken buraya koyar.
 * KeycloakSyncEventListener AFTER_COMMIT'te consume() ile alır ve
 * hemen invalidate eder; böylece şifre event nesnesine veya log'a düşmez.
 *
 * <p>TTL=60 saniye: DB commit + Keycloak HTTP çağrısı için yeterli;
 * timeout durumunda şifre otomatik temizlenir.
 */
@Component
public class PendingPasswordStore {

    private final Cache<UUID, String> cache;

    public PendingPasswordStore() {
        this(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10_000)
                .build());
    }

    /** Test amacıyla özel cache enjeksiyonuna izin verir (Ticker ile zaman kontrolü). */
    PendingPasswordStore(Cache<UUID, String> cache) {
        this.cache = cache;
    }

    /**
     * Şifreyi eventId ile saklar.
     *
     * @param eventId  benzersiz event kimliği (UserCreatedEvent.eventId)
     * @param rawPassword  plain-text şifre (encode edilmemiş; Keycloak'a yazılacak)
     */
    public void put(UUID eventId, String rawPassword) {
        cache.put(eventId, rawPassword);
    }

    /**
     * Şifreyi alır ve hemen siler (tek kullanım).
     *
     * @param eventId  UserCreatedEvent.eventId
     * @return şifre; TTL dolmuşsa veya hiç eklenmemişse {@code null}
     */
    public String consume(UUID eventId) {
        String password = cache.getIfPresent(eventId);
        cache.invalidate(eventId);
        return password;
    }
}
