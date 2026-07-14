package com.tav.userservice.controller;

import com.tav.userservice.config.SecurityConfig;
import com.tav.userservice.security.GatewayAuthFilter;
import com.tav.userservice.service.UserService;
import com.tav.userservice.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({
        SecurityConfig.class,
        GatewayAuthFilter.class,
        com.tav.userservice.common.exception.GlobalExceptionHandler.class,
        org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class
})
@TestPropertySource(properties = "app.gateway.secret=test-secret")
@DisplayName("UserController — Yetkilendirme ve Rol Güvenlik Testleri")
class UserControllerSecurityTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserService userService;

    private static final String SECRET = "test-secret";

    @Test
    @DisplayName("GET /api/users - ADMIN rolü ile erişim -> 200 OK")
    void getActiveUsers_withAdmin_returns200() throws Exception {
        when(userService.getAllActiveUsers()).thenReturn(List.of(TestDataFactory.buildUserDto()));

        mockMvc.perform(get("/api/users")
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Name", "adminuser")
                        .header("X-User-Roles", "ROLE_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/users - Geçersiz rol (OPERATION_OFFICER) ile erişim -> 403 Forbidden")
    void getActiveUsers_withOperationOfficer_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Name", "operatoruser")
                        .header("X-User-Roles", "ROLE_OPERATION_OFFICER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Yetkisiz işlem"));
    }

    @Test
    @DisplayName("GET /api/users - Geçersiz rol (BI_SPECIALIST) ile erişim -> 403 Forbidden")
    void getActiveUsers_withBiSpecialist_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Name", "analystuser")
                        .header("X-User-Roles", "ROLE_BI_SPECIALIST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Yetkisiz işlem"));
    }
}
