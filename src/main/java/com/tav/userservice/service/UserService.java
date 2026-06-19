package com.tav.userservice.service;

import com.tav.userservice.dto.UserCreateRequest;
import com.tav.userservice.dto.UserDto;
import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.entity.User;
import com.tav.userservice.entity.UserRole;
import com.tav.userservice.repository.RoleRepository;
import com.tav.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

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
        user.setIsActive(true);

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
