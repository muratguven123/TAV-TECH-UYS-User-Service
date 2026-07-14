package com.tav.userservice.integration;

// TA-002: DEF-001 regression guard — Keycloak down → is_active=false

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

/**
 * TA-002: DEF-001 regression guard.
 *
 * Senaryo: Keycloak URL geçersiz → 3 retry tükenir → recover() → kullanıcı is_active=false kalır.
 *
 * Ayrı IT sınıfı: UserServiceIT'den farklı bir Spring context gerekiyor
 * (geçersiz Keycloak URL ile). @DynamicPropertySource geçersiz URL set eder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationContainersConfig.class)
@ActiveProfiles("it")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceKeycloakDownIT {

    private static final String GATEWAY_SECRET = "test-gateway-secret";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;

    // Geçersiz Keycloak URL — bağlantı hatası → retry → recover → is_active=false
    @DynamicPropertySource
    static void keycloakInvalidUrl(DynamicPropertyRegistry reg) {
        reg.add("app.keycloak.server-url", () -> "http://localhost:19999"); // hiçbir şey yok
    }

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    @Order(1)
    @DisplayName("TA-002/DEF-001: Keycloak erişilemez → 201 (DB başarılı), is_active=false kalır")
    void createUser_keycloakDown_userRemainsDisabled() throws Exception {
        // given
        String username = "orphan.user";
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", "orphan@test.tav",
                "password", "Strong#Pass1",
                "roles", Set.of(RoleName.OPERATION_OFFICER)
        ));

        // when — DB transaction başarılı → 201
        mockMvc.perform(post("/api/users")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Name", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // then — 3 retry (2s + 4s backoff) tükenir → is_active=false kalır
        // Awaitility: retry'ların bitmesini bekle (max 15s)
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var user = userRepository.findByUsername(username).orElseThrow(
                            () -> new AssertionError("Kullanıcı DB'de bulunamadı: " + username));
                    assertThat(user.getIsActive())
                            .as("TA-002/DEF-001: Keycloak down → is_active=false kalmalı")
                            .isFalse();
                });
    }

    @Test
    @Order(2)
    @DisplayName("TA-002/DEF-001: Keycloak erişilemez → kullanıcı DB'de kayıtlı (transaction commit)")
    void createUser_keycloakDown_userPersistedInDb() throws Exception {
        // given
        String username = "db.persisted.user";
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", "db.persisted@test.tav",
                "password", "Strong#Pass1",
                "roles", Set.of()
        ));

        // when
        mockMvc.perform(post("/api/users")
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Name", "admin")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // then — kullanıcı DB'de mevcut
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userRepository.findByUsername(username))
                        .as("Kullanıcı DB'de kayıtlı olmalı (transaction commit'lendi)")
                        .isPresent());
    }
}
