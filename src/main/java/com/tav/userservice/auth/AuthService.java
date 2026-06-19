package com.tav.userservice.auth;

import com.tav.userservice.auth.dto.*;
import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.entity.User;
import com.tav.userservice.entity.UserRole;
import com.tav.userservice.repository.RoleRepository;
import com.tav.userservice.repository.UserRepository;
import com.tav.userservice.security.JwtTokenProvider;
import com.tav.userservice.security.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Kullanıcı adı zaten kullanımda: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-posta zaten kullanımda: " + request.email());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setIsActive(true);

        User saved = userRepository.save(user);
        Set<RoleName> roleNames = resolveRoleNames(request.roles());
        Set<String> roleStrings = new HashSet<>();

        for (RoleName roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalArgumentException("Rol bulunamadı: " + roleName));
            saved.getUserRoles().add(new UserRole(saved, role));
            roleStrings.add(roleName.name());
        }
        userRepository.save(saved);

        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(),
                roleStrings, saved.getIsActive());
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String token = jwtTokenProvider.generateToken(authentication);
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .collect(Collectors.toSet());

        return new AuthResponse(token, "Bearer", request.username(), roles,
                jwtTokenProvider.getValidityMs());
    }

    /**
     * Token'ı blacklist'e ekler.
     * Authorization header'dan çözülen ham token beklenir ("Bearer " prefix'i olmadan).
     */
    public void logout(String rawToken) {
        try {
            Claims claims = jwtTokenProvider.parseClaims(rawToken);
            String jti = claims.getId();
            long remainingMs = claims.getExpiration().getTime() - new Date().getTime();
            tokenBlacklistService.blacklist(jti, remainingMs);
        } catch (Exception e) {
            // Geçersiz token — logout zaten gerçekleşmiş sayılır
        }
    }

    private Set<RoleName> resolveRoleNames(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of(RoleName.OPERATION_OFFICER);
        }
        Set<RoleName> result = new HashSet<>();
        for (String name : roleNames) {
            result.add(RoleName.valueOf(name));
        }
        return result;
    }
}
