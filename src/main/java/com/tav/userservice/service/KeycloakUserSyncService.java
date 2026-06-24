package com.tav.userservice.service;

import com.tav.userservice.entity.RoleName;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kullanıcı oluşturma işlemini Keycloak ile senkronize eder.
 *
 * Akış:
 * 1. Keycloak'ta kullanıcı oluştur (POST /admin/realms/{realm}/users)
 * 2. Şifreyi geçici olmayan credential olarak set et
 * 3. Realm rollerini ata (POST /admin/realms/{realm}/users/{id}/role-mappings/realm)
 *
 * Hata durumunda RuntimeException fırlatır → UserService @Transactional rollback devreye girer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserSyncService {

    private final Keycloak keycloakAdmin;

    @Value("${app.keycloak.realm}")
    private String realm;

    /**
     * Kullanıcıyı Keycloak'ta oluşturur ve rolleri atar.
     *
     * @param username  kullanıcı adı
     * @param email     e-posta
     * @param password  düz metin şifre (Keycloak hash'ler)
     * @param roleNames atanacak roller
     * @throws IllegalStateException Keycloak'a yazma başarısız olursa
     */
    public void createUser(String username, String email, String password, Set<RoleName> roleNames) {
        RealmResource realmResource = keycloakAdmin.realm(realm);

        // 1. Kullanıcı temsili oluştur
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(true);

        // 2. Şifreyi credential olarak set et (geçici değil)
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        // 3. Kullanıcıyı oluştur
        try (Response response = realmResource.users().create(user)) {
            int status = response.getStatus();
            if (status != 201) {
                String body = response.readEntity(String.class);
                log.error("Keycloak kullanıcı oluşturma başarısız — status: {}, body: {}", status, body);
                throw new IllegalStateException(
                        "Keycloak kullanıcı oluşturma başarısız: HTTP " + status + " — " + body);
            }
        }

        // 4. Oluşturulan kullanıcının ID'sini al
        String keycloakUserId = realmResource.users().search(username, true)
                .stream()
                .findFirst()
                .map(UserRepresentation::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Keycloak'ta kullanıcı oluşturuldu ama bulunamadı: " + username));

        // 5. Rol ataması yap
        if (roleNames != null && !roleNames.isEmpty()) {
            List<RoleRepresentation> rolesToAssign = roleNames.stream()
                    .map(roleName -> {
                        RoleRepresentation role = realmResource.roles().get(roleName.name()).toRepresentation();
                        if (role == null) {
                            throw new IllegalStateException(
                                    "Keycloak realm'ında rol bulunamadı: " + roleName.name());
                        }
                        return role;
                    })
                    .collect(Collectors.toList());

            realmResource.users().get(keycloakUserId)
                    .roles()
                    .realmLevel()
                    .add(rolesToAssign);
        }

        log.info("Keycloak senkronizasyonu tamamlandı: kullanıcı={}, roller={}", username, roleNames);
    }
}
