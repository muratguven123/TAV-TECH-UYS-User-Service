package com.tav.userservice.event;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PendingPasswordStore birim testleri.
 *
 * FIX: DEF-002 — şifre yalnızca bir kez okunabilir; ikinci consume null döner.
 */
@DisplayName("PendingPasswordStore — Şifre tampon mağazası testleri")
class PendingPasswordStoreTest {

    // ------------------------------------------------------------------ put/consume

    @Test
    @DisplayName("put sonrası consume şifreyi döner ve invalidate eder; ikinci consume null döner")
    void put_thenConsume_returnsPasswordAndInvalidates() {
        // given
        PendingPasswordStore store = new PendingPasswordStore();
        UUID eventId = UUID.randomUUID();
        String rawPassword = "s3cr3t!";

        // when
        store.put(eventId, rawPassword);
        String first  = store.consume(eventId);
        String second = store.consume(eventId);

        // then
        assertThat(first).isEqualTo(rawPassword);
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("Bilinmeyen eventId için consume null döner")
    void consume_unknownEventId_returnsNull() {
        // given
        PendingPasswordStore store = new PendingPasswordStore();

        // when
        String result = store.consume(UUID.randomUUID());

        // then
        assertThat(result).isNull();
    }

    // ------------------------------------------------------------------ TTL

    @Test
    @DisplayName("TTL dolunca consume null döner (FakeTicker ile zaman ilerletilir)")
    void consume_afterTtl_returnsNull() {
        // given — özel Ticker ile TTL 60s'ı kontrol ederiz
        AtomicLong fakeClock = new AtomicLong(0);
        Cache<UUID, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(10_000)
                .ticker(() -> fakeClock.get())
                .build();
        PendingPasswordStore store = new PendingPasswordStore(cache);
        UUID eventId = UUID.randomUUID();
        store.put(eventId, "password123");

        // TTL'yi aşacak kadar ilerlet (61 saniye = nanosecond cinsinden)
        fakeClock.set(TimeUnit.SECONDS.toNanos(61));
        cache.cleanUp(); // Caffeine lazy eviction; cleanUp zorla tetikler

        // when
        String result = store.consume(eventId);

        // then
        assertThat(result).isNull();
    }

    // ------------------------------------------------------------------ toString/log güvenlik

    @Test
    @DisplayName("UserCreatedEvent toString() içinde password substring bulunmaz")
    void userCreatedEvent_toString_doesNotContainPassword() {
        // given — DEF-002: record'da password alanı artık yok
        com.tav.userservice.entity.RoleName role = com.tav.userservice.entity.RoleName.OPERATION_OFFICER;
        UserCreatedEvent event = new UserCreatedEvent(
                UUID.randomUUID(),
                "alice",
                "alice@example.com",
                java.util.Set.of(role)

        );

        // when
        String str = event.toString();

        // then — "password" kelimesi log'a düşemez
        assertThat(str).doesNotContainIgnoringCase("password");
        assertThat(str).doesNotContainIgnoringCase("s3cr3t");
        assertThat(str).contains("alice");
    }
}
