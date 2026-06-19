package com.tav.userservice.security;

import com.tav.userservice.entity.User;
import com.tav.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByIdWithRoles(
                userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + username))
                        .getId()
        ).orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + username));

        var authorities = user.getUserRoles().stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName().name()))
                .collect(Collectors.toSet());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getIsActive(),
                true, true, true,
                authorities
        );
    }
}
