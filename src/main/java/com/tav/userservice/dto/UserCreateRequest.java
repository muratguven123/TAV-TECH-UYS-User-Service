package com.tav.userservice.dto;

import com.tav.userservice.entity.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@NoArgsConstructor
public class UserCreateRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(
            regexp = "^[A-Za-z0-9_.]+$",
            message = "Kullanıcı adı yalnızca harf, rakam, alt çizgi ve nokta içerebilir; boşluk kullanılamaz"
    )
    private String username;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 60)
    private String password;

    private Set<RoleName> roles;
}
