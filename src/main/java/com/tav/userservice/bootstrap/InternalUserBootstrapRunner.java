package com.tav.userservice.bootstrap;

import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.entity.User;
import com.tav.userservice.entity.UserRole;
import com.tav.userservice.repository.RoleRepository;
import com.tav.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bootstrap.internal-users", havingValue = "true", matchIfMissing = false)
@Slf4j
public class InternalUserBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.internal-users.flight-service.username}")
    private String flightUser;

    @Value("${app.internal-users.flight-service.password}")
    private String flightPass;

    @Value("${app.internal-users.fas.username}")
    private String fasUser;

    @Value("${app.internal-users.fas.password}")
    private String fasPass;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureUser(flightUser, flightPass, "Flight Service");
        ensureUser(fasUser, fasPass, "FAS");
    }

    private void ensureUser(String username, String rawPassword, String label) {
        if (userRepository.existsByUsername(username)) {
            log.info("Internal user '{}' zaten var, atlanıyor ({})", username, label);
            return;
        }

        Role role = roleRepository.findByName(RoleName.OPERATION_OFFICER)
                .orElseThrow(() -> new IllegalStateException("OPERATION_OFFICER rolü bulunamadı"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@internal.tav.uys");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setIsActive(true);

        User saved = userRepository.save(user);
        saved.getUserRoles().add(new UserRole(saved, role));
        userRepository.save(saved);

        log.info("Internal user '{}' oluşturuldu ({})", username, label);
    }
}
