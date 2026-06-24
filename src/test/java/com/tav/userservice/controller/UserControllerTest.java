package com.tav.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tav.userservice.dto.UserCreateRequest;
import com.tav.userservice.entity.RoleName;
import com.tav.userservice.security.GatewayAuthFilter;
import com.tav.userservice.service.UserService;
import com.tav.userservice.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.gateway.secret=test-secret")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean GatewayAuthFilter gatewayAuthFilter;

    // ---------------------------------------------------------------- GET list

    @Test
    @DisplayName("GET /api/users: aktif kullanıcılar 200 ile döner")
    void getActiveUsers_returns200WithArray() throws Exception {
        // given
        when(userService.getAllActiveUsers()).thenReturn(List.of(TestDataFactory.buildUserDto()));

        // when / then
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value(TestDataFactory.USERNAME));
    }

    // ---------------------------------------------------------------- GET id

    @Test
    @DisplayName("GET /api/users/{id}: kayıt varsa 200 ile UserDto döner")
    void getUser_existing_returns200WithUserDto() throws Exception {
        // given
        when(userService.getUserById(1L)).thenReturn(TestDataFactory.buildUserDto());

        // when / then
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value(TestDataFactory.EMAIL));
    }

    @Test
    @DisplayName("GET /api/users/{id}: kayıt yoksa GlobalExceptionHandler 409 döner (IllegalArgumentException)")
    void getUser_notFound_returnsErrorStatus() throws Exception {
        // given
        when(userService.getUserById(99L))
                .thenThrow(new IllegalArgumentException("User not found: 99"));

        // when / then
        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().is4xxClientError());
    }

    // -------------------------------------------------------------- POST create

    @Test
    @DisplayName("POST /api/users: geçerli request 201 ile yanıtlanır")
    void createUser_validRequest_returns201() throws Exception {
        // given
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenReturn(TestDataFactory.buildUserDto());
        String body = objectMapper.writeValueAsString(
                jsonRequest(TestDataFactory.USERNAME, TestDataFactory.EMAIL, TestDataFactory.PASSWORD, Set.of(RoleName.OPERATION_OFFICER)));

        // when / then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(TestDataFactory.USERNAME));
    }

    @Test
    @DisplayName("POST /api/users: şifre 8 karakterden kısa ise 400 döner")
    void createUser_shortPassword_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(
                jsonRequest(TestDataFactory.USERNAME, TestDataFactory.EMAIL, "short", Set.of()));

        // when / then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users: boş username @NotBlank ihlali 400 döner")
    void createUser_blankUsername_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(
                jsonRequest("", TestDataFactory.EMAIL, TestDataFactory.PASSWORD, Set.of()));

        // when / then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users: geçersiz e-posta formatı 400 döner")
    void createUser_invalidEmail_returns400() throws Exception {
        // given
        String body = objectMapper.writeValueAsString(
                jsonRequest(TestDataFactory.USERNAME, "not-an-email", TestDataFactory.PASSWORD, Set.of()));

        // when / then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/users: username zaten varsa servis hatası yansıtılır (409)")
    void createUser_duplicateUsername_returnsConflict() throws Exception {
        // given
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenThrow(new IllegalArgumentException("Username already taken: x"));
        String body = objectMapper.writeValueAsString(
                jsonRequest(TestDataFactory.USERNAME, TestDataFactory.EMAIL, TestDataFactory.PASSWORD, Set.of()));

        // when / then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------- GET /health

    @Test
    @DisplayName("GET /api/users/health: UP durumu ve userCount döner")
    void health_returns200WithStatus() throws Exception {
        // given
        when(userService.getUserCount()).thenReturn(7L);

        // when / then
        mockMvc.perform(get("/api/users/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.userCount").value(7));
    }

    // ------------------------------------------------------------------ helpers

    /** UserCreateRequest setter'sız; serialize için map veya doğrudan obj inşa et. */
    private static UserCreateRequest jsonRequest(String username, String email, String password, Set<RoleName> roles) {
        UserCreateRequest r = new UserCreateRequest();
        setField(r, "username", username);
        setField(r, "email", email);
        setField(r, "password", password);
        setField(r, "roles", roles);
        return r;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
