package com.tav.userservice.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Kullanıcı adı boş olamaz") String username,
        @NotBlank(message = "Parola boş olamaz") String password
) {}
