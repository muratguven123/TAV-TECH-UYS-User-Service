package com.tav.userservice.util;

import com.tav.userservice.dto.UserCreateRequest;
import com.tav.userservice.dto.UserDto;
import com.tav.userservice.entity.Role;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.entity.User;
import com.tav.userservice.entity.UserRole;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/**
 * user-service için ortak test fixture builder'ları.
 * FlightService/TestDataFactory ile aynı stil.
 */
public class TestDataFactory {

    public static final Long   USER_ID  = 1L;
    public static final String USERNAME = "ahmet.yilmaz";
    public static final String EMAIL    = "ahmet.yilmaz@tav.aero";
    public static final String PASSWORD = "Strong#Pass1";
    public static final String ENCODED_PASSWORD = "$2a$10$encodedFakeHash";

    public static final OffsetDateTime FIXED_TIME =
            OffsetDateTime.of(2026, 6, 23, 10, 0, 0, 0, ZoneOffset.UTC);

    public static UserCreateRequest buildCreateRequest() {
        return buildCreateRequest(Set.of(RoleName.OPERATION_OFFICER));
    }

    public static UserCreateRequest buildCreateRequest(Set<RoleName> roles) {
        UserCreateRequest req = new UserCreateRequest();
        setField(req, "username", USERNAME);
        setField(req, "email", EMAIL);
        setField(req, "password", PASSWORD);
        setField(req, "roles", roles);
        return req;
    }

    public static User buildUser() {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername(USERNAME);
        u.setEmail(EMAIL);
        u.setPassword(ENCODED_PASSWORD);
        u.setIsActive(true);
        u.setUserRoles(new HashSet<>());
        return u;
    }

    public static User buildUserWithRoles(RoleName... roleNames) {
        User u = buildUser();
        for (RoleName rn : roleNames) {
            Role r = buildRole(rn);
            u.getUserRoles().add(new UserRole(u, r));
        }
        return u;
    }

    public static Role buildRole(RoleName name) {
        Role role = new Role();
        // id final değil, setter var
        role.setName(name);
        setField(role, "id", name.ordinal() + 1);
        return role;
    }

    public static UserDto buildUserDto() {
        return UserDto.builder()
                .id(USER_ID)
                .username(USERNAME)
                .email(EMAIL)
                .isActive(true)
                .roles(Set.of(RoleName.OPERATION_OFFICER))
                .createdAt(FIXED_TIME)
                .updatedAt(FIXED_TIME)
                .build();
    }

    /** UserCreateRequest setter'sız ve @NoArgsConstructor; field'lara reflection ile yaz. */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Field set hatası: " + fieldName, e);
        }
    }
}
