package com.tav.userservice.auth;

import com.tav.userservice.auth.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Logout — token'ı Redis blacklist'e ekler.
     *
     * İstemci Authorization: Bearer <token> header'ını gönderir.
     * Bu endpoint /api/auth/** altında olduğu için Gateway JWT'yi doğrulamadan geçirir.
     * Blacklist işlemi user-service içinde yapılır.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            authService.logout(bearer.substring(7));
        }
        return ResponseEntity.ok(Map.of(
                "timestamp", Instant.now().toString(),
                "message", "Başarıyla çıkış yapıldı"
        ));
    }
}
