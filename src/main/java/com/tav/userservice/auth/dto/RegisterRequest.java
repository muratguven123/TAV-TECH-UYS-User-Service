package com.tav.userservice.auth.dto;

import com.tav.userservice.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record RegisterRequest(
        @NotBlank(message = "Kullanıcı adı boş olamaz") String username,
        @NotBlank(message = "E-posta boş olamaz") @Email(message = "Geçerli bir e-posta giriniz") String email,
        @NotBlank(message = "Parola boş olamaz") @ValidPassword String password,
        Set<String> roles
) {}
