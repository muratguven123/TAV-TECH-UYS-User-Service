package com.tav.userservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GatewayAuthFilter integration testleri — tam Spring Security filter zinciri.
 *
 * Unit test (GatewayAuthFilterTest) filtreyi izole test eder.
 * Bu IT, gerçek Security konfigürasyonu + filtre zinciri + endpoint bağlamasını doğrular.
 *
 * Keycloak Testcontainer: bu filter Keycloak ile iletişim kurmaz;
 * ancak Spring context ayağa kalkmak için KeycloakAdminConfig bean'i gerekir.
 * KeycloakTestContainer JVM singleton'ı bu ihtiyacı karşılar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationContainersConfig.class)
@ActiveProfiles("it")
class GatewayAuthFilterIT {

    private static final String VALID_SECRET = "test-gateway-secret";

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void keycloakProps(DynamicPropertyRegistry reg) {
        reg.add("app.keycloak.server-url", KeycloakTestContainer.INSTANCE::getAuthServerUrl);
    }

    // ─── TESTLER ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Gecerli Gateway Secret + X-User-Name → 200 (veya ilgili is yaniti)")
    void validGatewaySecret_withUserName_requestPasses() throws Exception {
        // given / when / then
        mockMvc.perform(get("/api/users/health")
                        .header("X-Gateway-Secret", VALID_SECRET)
                        .header("X-User-Name", "test-operator")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("X-Gateway-Secret eksik → 401")
    void missingGatewaySecret_returns401() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-User-Name", "test-operator")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("X-Gateway-Secret yanlis → 401")
    void wrongGatewaySecret_returns401() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Gateway-Secret", "totally-wrong-secret")
                        .header("X-User-Name", "test-operator")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("X-User-Name eksik → 401 (gateway-forwarded isteklerde olmazmali)")
    void missingUserName_returns401() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Gateway-Secret", VALID_SECRET)
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Swagger endpoint — gateway filtresi bypass eder")
    void swaggerEndpoint_bypassesGatewayFilter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator health — gateway filtresi bypass eder")
    void actuatorHealth_bypassesGatewayFilter() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Timing-safe karsılastırma — cok kismi secret → 401 (partial match reddedilir)")
    void partialSecretPrefix_returns401() throws Exception {
        // Timing-safe secretsEqual() metodu kısmi eşleşmeyi de reddeder
        String partialSecret = VALID_SECRET.substring(0, 5);
        mockMvc.perform(get("/api/users")
                        .header("X-Gateway-Secret", partialSecret)
                        .header("X-User-Name", "operator")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rol bilgisi olmadan gecerli secret — authentication gecerli ama yetki yok")
    void validSecret_noRoles_returns403orOk() throws Exception {
        // /api/users/health sadece authenticated gerektirir, rol gerektirmez
        mockMvc.perform(get("/api/users/health")
                        .header("X-Gateway-Secret", VALID_SECRET)
                        .header("X-User-Name", "viewer")
                        // X-User-Roles header yok
                )
                .andExpect(status().isOk()); // health endpoint her authenticated kullanıcıya açık
    }
}
