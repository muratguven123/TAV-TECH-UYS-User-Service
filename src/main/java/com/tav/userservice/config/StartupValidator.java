package com.tav.userservice.config;

import com.tav.userservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupValidator implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        long roleCount = roleRepository.count();
        log.info("=== user-service DB bağlantısı başarılı | roles tablosunda {} kayıt ===", roleCount);
        if (roleCount == 0) {
            log.warn("Roles tablosu boş — init-user-schema.sql çalıştırıldı mı?");
        }
    }
}
