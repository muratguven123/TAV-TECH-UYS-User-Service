package com.tav.userservice.event;

import com.tav.userservice.entity.RoleName;

import java.util.Set;
import java.util.UUID;

/**
 * UserService.createUser() DB'ye kaydettikten sonra bu event publish edilir.
 * KeycloakSyncEventListener, transaction commit'ten SONRA bu event'i dinleyerek
 * Keycloak senkronizasyonunu gerçekleştirir.
 *
 * Neden record: immutable, boilerplate yok, equals/hashCode/toString ücretsiz.
 *
 * DEF-001 FIX: userId alanı eklendi — listener activate edebilsin.
 * DEF-002: password alanı kasıtlı olarak bu record'da tutulmaz.
 * Şifre PendingPasswordStore'da eventId üzerinden TTL=60s ile saklanır;
 * listener consume() ile alıp invalidate eder.
 */
public record UserCreatedEvent(
        UUID eventId,
        Long userId,            // DEF-001: Keycloak sync başarılıysa this ID activate edilir
        String username,
        String email,
        Set<RoleName> roles
) {
    // Backwards-compatible convenience ctor: older tests/clients created the event
    // without userId (DEF-001 added it later). Allow constructing with null userId.
    public UserCreatedEvent(UUID eventId, String username, String email, Set<RoleName> roles) {
        this(eventId, null, username, email, roles);
    }
}
