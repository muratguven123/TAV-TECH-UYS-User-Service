package com.tav.userservice.integration;

import com.tav.userservice.entity.RoleName;
import com.tav.userservice.service.KeycloakUserSyncService;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KeycloakUserSyncService integration testleri — gerçek Keycloak Testcontainer.
 *
 * Test kapsamı:
 *  - Kullanıcı oluşturma → Keycloak Admin API ile doğrulama
 *  - Şifre doğru formatta yazılıyor mu (credential endpoint doğrulaması)
 *  - Rol ataması çalışıyor mu
 *  - Duplicate kullanıcı → 409 fırlatılıyor mu (mevcut non-idempotent davranış)
 *
 * NOT: Bu servis PostgreSQL'e bağımlı değil; sadece Keycloak TC gerekir.
 * Ancak IntegrationContainersConfig'i import ediyoruz çünkü
 * Spring context PostgreSQL olmadan ayağa kalkmaz (UserRepository bağımlılığı).
 */
@SpringBootTest
@Import(IntegrationContainersConfig.class)
@ActiveProfiles("it")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakUserSyncServiceIT {

    @Autowired
    KeycloakUserSyncService syncService;

    @DynamicPropertySource
    static void keycloakProps(DynamicPropertyRegistry reg) {
        reg.add("app.keycloak.server-url", KeycloakTestContainer.INSTANCE::getAuthServerUrl);
    }

    @AfterEach
    void cleanKeycloak() {
        try (Keycloak admin = adminClient()) {
            admin.realm("uys-test").users().list().stream()
                    .filter(u -> !Set.of("testuser", "biuser").contains(u.getUsername()))
                    .forEach(u -> admin.realm("uys-test").users().get(u.getId()).remove());
        }
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    private Keycloak adminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(KeycloakTestContainer.INSTANCE.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username("admin")
                .password("admin")
                .build();
    }

    private List<UserRepresentation> searchInKeycloak(String username) {
        try (Keycloak admin = adminClient()) {
            return admin.realm("uys-test").users().search(username, true);
        }
    }

    // ─── TESTLER ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("syncUser → Keycloak'ta kullanici olusturulur")
    void syncUser_createsUserInKeycloak() {
        // given / when
        syncService.createUser("sync.officer", "sync.officer@test.tav",
                "Strong#Pass1", Set.of(RoleName.OPERATION_OFFICER));

        // then
        List<UserRepresentation> results = searchInKeycloak("sync.officer");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEmail()).isEqualTo("sync.officer@test.tav");
        assertThat(results.get(0).isEnabled()).isTrue();
        assertThat(results.get(0).isEmailVerified()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("syncUser → Keycloak'ta OPERATION_OFFICER rolu atanir")
    void syncUser_roleIsAssignedInKeycloak() {
        // given / when
        syncService.createUser("sync.bi", "sync.bi@test.tav",
                "Strong#Pass1", Set.of(RoleName.BI_SPECIALIST));

        // then — rol ataması doğru mu?
        try (Keycloak admin = adminClient()) {
            String userId = admin.realm("uys-test")
                    .users().search("sync.bi", true).get(0).getId();
            List<String> roleNames = admin.realm("uys-test")
                    .users().get(userId).roles().realmLevel().listEffective()
                    .stream().map(r -> r.getName()).toList();
            assertThat(roleNames).contains("BI_SPECIALIST");
        }
    }

    @Test
    @Order(3)
    @DisplayName("syncUser → Birden fazla rol atanabilir")
    void syncUser_multipleRolesAreAssigned() {
        // given / when
        syncService.createUser("sync.multi", "sync.multi@test.tav",
                "Strong#Pass1", Set.of(RoleName.OPERATION_OFFICER, RoleName.BI_SPECIALIST));

        // then
        try (Keycloak admin = adminClient()) {
            String userId = admin.realm("uys-test")
                    .users().search("sync.multi", true).get(0).getId();
            List<String> roleNames = admin.realm("uys-test")
                    .users().get(userId).roles().realmLevel().listEffective()
                    .stream().map(r -> r.getName()).toList();
            assertThat(roleNames).contains("OPERATION_OFFICER", "BI_SPECIALIST");
        }
    }

    @Test
    @Order(4)
    @DisplayName("syncUser → Rol listesi bos, sadece kullanici olusturulur")
    void syncUser_noRoles_userCreatedWithoutRoles() {
        // given / when
        syncService.createUser("sync.norole", "sync.norole@test.tav",
                "Strong#Pass1", Set.of());

        // then
        List<UserRepresentation> results = searchInKeycloak("sync.norole");
        assertThat(results).hasSize(1);
    }

    @Test
    @Order(5)
    @DisplayName("syncUser ikinci kez — duplicate kullanici → IllegalStateException (non-idempotent davranis)")
    void syncUser_duplicateUsername_throwsIllegalStateException() {
        // NOT: Mevcut implementasyon idempotent değildir.
        // İkinci çağrıda Keycloak 409 döner ve IllegalStateException fırlatılır.
        // Idempotency gerekiyorsa serviste "search-or-create" pattern'i implemente edilmeli.

        // given
        syncService.createUser("sync.dup", "sync.dup@test.tav",
                "Strong#Pass1", Set.of(RoleName.OPERATION_OFFICER));

        // when / then — ikinci çağrı exception fırlatır
        assertThatThrownBy(() ->
                syncService.createUser("sync.dup", "sync.dup2@test.tav",
                        "Strong#Pass1", Set.of(RoleName.OPERATION_OFFICER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("409");

        // Keycloak'ta yalnızca bir kayıt
        assertThat(searchInKeycloak("sync.dup")).hasSize(1);
    }

    @Test
    @Order(6)
    @DisplayName("syncUser → Sifre dogrulama: token alinabiliyor mu (direct grant)")
    void syncUser_credentialsCorrectlySet_tokenCanBeObtained() throws Exception {
        // given
        syncService.createUser("sync.cred", "sync.cred@test.tav",
                "Strong#Pass1", Set.of(RoleName.OPERATION_OFFICER));

        // when — Keycloak'tan token al (ROPC / direct grant)
        var httpClient = java.net.http.HttpClient.newHttpClient();
        String tokenUrl = KeycloakTestContainer.INSTANCE.getAuthServerUrl()
                + "/realms/uys-test/protocol/openid-connect/token";

        var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "grant_type=password" +
                        "&client_id=uys-gateway" +
                        "&client_secret=test-gateway-client-secret" +
                        "&username=sync.cred" +
                        "&password=Strong%23Pass1"))
                .build();

        var response = httpClient.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

        // then — token başarıyla alındı
        assertThat(response.statusCode())
                .as("Keycloak ROPC token endpoint 200 donmeli, sifre dogru set edilmis olmali")
                .isEqualTo(200);
        assertThat(response.body()).contains("access_token");
    }
}
