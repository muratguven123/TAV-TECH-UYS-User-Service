package com.tav.userservice.event;

import com.tav.userservice.entity.RoleName;

import java.util.Set;

/**
 * UserService.createUser() DB'ye kaydettikten sonra bu event publish edilir.
 * KeycloakSyncEventListener, transaction commit'ten SONRA bu event'i dinleyerek
 * Keycloak senkronizasyonunu gerçekleştirir.
 *
 * Neden record: immutable, boilerplate yok, equals/hashCode/toString ücretsiz.
 */
public record UserCreatedEvent(
        String username,
        String email,
        String password,
        Set<RoleName> roles
) {}
