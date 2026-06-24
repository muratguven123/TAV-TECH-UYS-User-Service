package com.tav.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Downstream gateway güvenlik filtresi.
 *
 * Gateway, isteği doğruladıktan sonra:
 *   - X-Gateway-Secret  : shared secret (iç ağ doğrulaması)
 *   - X-User-Name       : doğrulanmış kullanıcı adı
 *   - X-User-Roles      : virgülle ayrılmış roller
 * header'larını ekler ve Authorization header'ını kaldırır.
 *
 * Bu filtre gateway üzerinden gelmeyen istekleri reddeder.
 * /api/auth/** ve actuator/swagger path'leri bu filtreden muaftır.
 */
@Slf4j
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    @Value("${app.gateway.secret}")
    private String expectedGatewaySecret;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> BYPASS_PATHS = List.of(
            "/api/auth/**",
            "/actuator/health",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return BYPASS_PATHS.stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String gatewaySecret = request.getHeader("X-Gateway-Secret");
        if (!StringUtils.hasText(gatewaySecret) || !secretsEqual(gatewaySecret, expectedGatewaySecret)) {
            log.warn("Geçersiz ya da eksik X-Gateway-Secret: {}", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        String username = request.getHeader("X-User-Name");
        String rolesHeader = request.getHeader("X-User-Roles");

        if (!StringUtils.hasText(username)) {
            log.warn("X-User-Name header eksik: {}", request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        Set<SimpleGrantedAuthority> authorities = StringUtils.hasText(rolesHeader)
                ? Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet())
                : Set.of();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    /**
     * Timing-safe karşılaştırma. String.equals() karakter sayısı veya ilk farklı karakter
     * pozisyonuna göre erken döner; MessageDigest.isEqual() sabit zamanlıdır.
     */
    private boolean secretsEqual(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        byte[] a = provided.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
        response.getWriter().flush();
    }
}
