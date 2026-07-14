package com.tav.userservice.service;

import com.tav.userservice.dto.UserCreateRequest;
import com.tav.userservice.dto.UserDto;
import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.entity.User;
import com.tav.userservice.entity.UserRole;
import com.tav.userservice.event.PendingPasswordStore;
import com.tav.userservice.event.UserCreatedEvent;
import com.tav.userservice.repository.RoleRepository;
import com.tav.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    // FIX: DEF-002 — şifreyi event nesnesine koymak yerine store üzerinden taşı
    private final PendingPasswordStore pendingPasswordStore;

    @Transactional
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // DEF-001 FIX: Kullanıcıyı disabled olarak yarat.
        // Keycloak sync başarılı olunca activateUser() ile true yapılır.
        // Başarısız olursa kullanıcı DB'de var ama sisteme giremez → tutarlı state.
        user.setIsActive(false);

        User saved = userRepository.save(user);

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            for (RoleName roleName : request.getRoles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
                UserRole userRole = new UserRole(saved, role);
                saved.getUserRoles().add(userRole);
            }
            userRepository.save(saved);
        }

        // FIX: DEF-002 — şifreyi event'e koymak yerine store'a koy; event yalnızca eventId taşır.
        UUID eventId = UUID.randomUUID();
        pendingPasswordStore.put(eventId, request.getPassword());

        // Event publish et — Keycloak sync transaction COMMIT'ten sonra çalışır.
        // Bu sayede DB connection, HTTP çağrısı sırasında pool'a geri döner.
        eventPublisher.publishEvent(new UserCreatedEvent(
                eventId,
                saved.getId(),          // DEF-001: userId eklendi — listener activate edebilsin
                request.getUsername(),
                request.getEmail(),
                request.getRoles()
        ));

        // DB'den tekrar oku — created_at/updated_at trigger/DEFAULT değerlerini getirir
        return toDto(userRepository.findByIdWithRoles(saved.getId())
                .orElseThrow(() -> new IllegalStateException("User not found after save: " + saved.getId())));
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        User user = userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        return toDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllActiveUsers() {
        return userRepository.findAllActive().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUserCount() {
        return userRepository.count();
    }

    private UserDto toDto(User user) {
        Set<RoleName> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName())
                .collect(Collectors.toSet());

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .isActive(user.getIsActive())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
