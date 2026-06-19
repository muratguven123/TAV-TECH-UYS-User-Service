package com.tav.userservice.auth.dto;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String tokenType,
        String username,
        Set<String> roles,
        long expiresInMs
) {}
