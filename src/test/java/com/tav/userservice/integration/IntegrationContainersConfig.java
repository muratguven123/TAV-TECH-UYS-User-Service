package com.tav.userservice.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * IT testleri için ortak Testcontainer yapılandırması.
 *
 * PostgreSQLContainer → @ServiceConnection ile spring.datasource.* otomatik override edilir.
 * Keycloak container yönetimi: KeycloakTestContainer (JVM singleton) sınıfından yapılır.
 *
 * Her IT sınıfı @Import(IntegrationContainersConfig.class) ile bunu kullanır.
 * Spring Boot test context cache'i aynı konfigürasyonda aynı context'i yeniden kullanır.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("uys_users_test")
                .withUsername("test")
                .withPassword("test");
    }
}
