package com.tav.userservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * JWT token revocation — Redis blacklist.
 *
 * Logout yapıldığında token'ın jti'si Redis'e yazılır.
 * TTL = token'ın kalan süresi → süresi dolan token'lar Redis'ten otomatik silinir.
 * Gateway her istekte bu servisle aynı Redis'i kontrol eder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Token'ı blacklist'e ekle.
     * @param jti   JWT ID (token içindeki benzersiz tanımlayıcı)
     * @param ttlMs token'ın kalan geçerlilik süresi (ms) — bu süre sonunda Redis'ten otomatik silinir
     */
    public void blacklist(String jti, long ttlMs) {
        if (ttlMs <= 0) return; // Zaten süresi dolmuş, eklemeye gerek yok
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "revoked", Duration.ofMillis(ttlMs));
        log.debug("Token blacklisted: jti={}, ttlMs={}", jti, ttlMs);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
