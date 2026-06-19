package com.tav.userservice.entity;

/**
 * Sistem rolleri.
 * Authority olarak "ROLE_" + name() formatında kullanılır.
 * Keycloak'a geçişte bu değerler realm role'larıyla eşleşecek.
 */
public enum RoleName {
    OPERATION_OFFICER,
    BI_SPECIALIST
}
