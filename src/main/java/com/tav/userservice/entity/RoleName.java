package com.tav.userservice.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Sistem rolleri.
 * Authority olarak "ROLE_" + name() formatında kullanılır.
 * Keycloak'a geçişte bu değerler realm role'larıyla eşleşir.
 *
 * <p>Not: Yeni değerler enum'un SONUNA eklenir; {@code ordinal()} değerleri
 * (TestDataFactory ve DB seed sırası buna dayanır) böylece korunur.</p>
 */
public enum RoleName {
    OPERATION_OFFICER,
    BI_SPECIALIST,
    ADMIN;

    /**
     * JSON'dan deserialize: {@code ROLE_ADMIN} ve {@code ADMIN} aynı enum değerine çözülür.
     */
    @JsonCreator
    public static RoleName fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Rol adı boş olamaz");
        }
        String normalized = value.trim();
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        return RoleName.valueOf(normalized);
    }
}
