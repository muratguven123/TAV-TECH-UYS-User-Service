package com.tav.userservice.auth.dto;

import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String email,
        Set<String> roles,
        boolean enabled
) {}
