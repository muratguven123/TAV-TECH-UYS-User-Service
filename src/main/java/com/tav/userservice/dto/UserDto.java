package com.tav.userservice.dto;

import com.tav.userservice.entity.RoleName;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Set;

@Getter
@Builder
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private Boolean isActive;
    private Set<RoleName> roles;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
