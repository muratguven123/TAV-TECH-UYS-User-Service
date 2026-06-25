package com.tav.userservice.integration;

import dasniko.testcontainers.keycloak.KeycloakContainer;

/**
 * JVM yaşam süresi boyunca tek bir Keycloak container yönetir.
 *
 * Pahalı Keycloak başlatmasını (≈ 5-10 sn) bir kez yapar; tüm IT sınıfları
 * bu statik instance'ı paylaşır. Spring Boot test context cache'i sayesinde
 * @DynamicPropertySource değerleri eşleştiği sürece context da yeniden başlamaz.
 *
 * Kullanım:
 *   @DynamicPropertySource
 *   static void keycloakProps(DynamicPropertyRegistry reg) {
 *       reg.add("app.keycloak.server-url", KeycloakTestContainer.INSTANCE::getAuthServerUrl);
 *   }
 */
public final class KeycloakTestContainer {

    public static final KeycloakContainer INSTANCE;

    static {
        INSTANCE = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
                .withRealmImportFile("keycloak/test-realm.json")
                .withAdminUsername("admin")
                .withAdminPassword("admin");
        INSTANCE.start();
    }

    private KeycloakTestContainer() {
        // utility sınıfı
    }
}
