package com.tav.userservice.bootstrap;

import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Uygulama başlangıcında {@link RoleName} enum'undaki tüm rollerin
 * {@code roles} tablosunda bulunmasını garanti eder.
 *
 * <p>Roller normalde {@code init-user-schema.sql} (docker-entrypoint-initdb.d)
 * ile seed edilir. Bu runner, enum'a yeni bir rol eklendiğinde (ör. {@code ADMIN})
 * DB'de karşılığı yoksa onu idempotent biçimde oluşturur; böylece
 * {@code @PreAuthorize("hasRole('ADMIN')")} kontrolleri ve rol atamaları
 * (KeycloakUserSyncService dahil) sorunsuz çalışır.</p>
 *
 * <p><b>Idempotent:</b> var olan roller atlanır — mevcut kayıtlara dokunulmaz.
 * <b>Savunmacı:</b> tek bir rolün eklenmesi başarısız olsa bile (ör. eski bir
 * CHECK constraint'e sahip DB) uygulama başlangıcı kesilmez; hata yalnızca
 * uyarı olarak loglanır.</p>
 *
 * <p>{@link org.springframework.core.annotation.Order @Order(0)} ile
 * {@code StartupValidator}'dan önce çalışır; böylece rol sayısı logu
 * seed sonrası durumu yansıtır.</p>
 */
@Component
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class RoleSeedRunner implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (RoleName roleName : RoleName.values()) {
            if (roleRepository.findByName(roleName).isPresent()) {
                continue;
            }
            try {
                Role role = new Role();
                role.setName(roleName);
                roleRepository.save(role);
                log.info("Eksik rol seed edildi: {}", roleName);
            } catch (Exception ex) {
                // Örn. eski bir CHECK constraint yeni rolü reddedebilir; başlangıcı düşürme.
                log.warn("Rol seed edilemedi: {} — {}", roleName, ex.getMessage());
            }
        }
    }
}
