package com.tav.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GatewayAuthFilterTest {

    private static final String SECRET = "test-gateway-secret";

    private GatewayAuthFilter filter;

    @Mock FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new GatewayAuthFilter();
        ReflectionTestUtils.setField(filter, "expectedGatewaySecret", SECRET);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ----------------------------------------------------------- happy path

    @Test
    @DisplayName("Geçerli gateway secret: SecurityContext set edilir ve chain ilerletilir")
    void validGatewaySecret_passesAndSetsAuthenticationFromHeaders() throws Exception {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        req.addHeader("X-Gateway-Secret", SECRET);
        req.addHeader("X-User-Name", "ahmet");
        req.addHeader("X-User-Roles", "ROLE_OPERATION_OFFICER,ROLE_BI_SPECIALIST");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // when
        filter.doFilter(req, res, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("ahmet");
        assertThat(auth.getAuthorities()).hasSize(2);
        verify(chain).doFilter(req, res);
    }

    // -------------------------------------------------------- missing/wrong secret

    @Test
    @DisplayName("Eksik X-Gateway-Secret: 401 döner ve chain çağrılmaz")
    void missingGatewaySecret_returns401() throws Exception {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // when
        filter.doFilter(req, res, chain);

        // then
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("Yanlış X-Gateway-Secret: 401 döner")
    void wrongGatewaySecret_returns401() throws Exception {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        req.addHeader("X-Gateway-Secret", "WRONG");
        req.addHeader("X-User-Name", "ahmet");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // when
        filter.doFilter(req, res, chain);

        // then
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("X-User-Name eksikse 401 döner")
    void missingUserName_returns401() throws Exception {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        req.addHeader("X-Gateway-Secret", SECRET);
        MockHttpServletResponse res = new MockHttpServletResponse();

        // when
        filter.doFilter(req, res, chain);

        // then
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(req, res);
    }

    // -------------------------------------------------- roles header parsing

    @Test
    @DisplayName("Roller virgülle ayrılır ve trim edilir")
    void multipleRoles_parsedFromCommaSeparatedHeader() throws Exception {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        req.addHeader("X-Gateway-Secret", SECRET);
        req.addHeader("X-User-Name", "u");
        req.addHeader("X-User-Roles", " ROLE_A , ROLE_B , ROLE_C ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // when
        filter.doFilter(req, res, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("ROLE_A", "ROLE_B", "ROLE_C");
    }

    @Test
    @DisplayName("Rol header'ı boşsa boş authority listesi atanır")
    void emptyRolesHeader_setsEmptyAuthorities() throws Exception {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");
        req.addHeader("X-Gateway-Secret", SECRET);
        req.addHeader("X-User-Name", "u");
        MockHttpServletResponse res = new MockHttpServletResponse();

        // when
        filter.doFilter(req, res, chain);

        // then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).isEmpty();
        verify(chain).doFilter(req, res);
    }

    // ----------------------------------------------------------- bypass paths

    @Test
    @DisplayName("/actuator/health: filtre atlanır")
    void publicPath_actuatorHealth_skipsFilter() {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");

        // when
        boolean shouldSkip = invokeShouldNotFilter(req);

        // then
        assertThat(shouldSkip).isTrue();
    }

    @Test
    @DisplayName("/swagger-ui/index.html: filtre atlanır")
    void publicPath_swaggerUi_skipsFilter() {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/swagger-ui/index.html");

        // when
        boolean shouldSkip = invokeShouldNotFilter(req);

        // then
        assertThat(shouldSkip).isTrue();
    }

    @Test
    @DisplayName("/api/users: korunan path, filtre çalışır")
    void protectedPath_apiUsers_runsFilter() {
        // given
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users");

        // when
        boolean shouldSkip = invokeShouldNotFilter(req);

        // then
        assertThat(shouldSkip).isFalse();
    }

    private boolean invokeShouldNotFilter(HttpServletRequest req) {
        try {
            java.lang.reflect.Method m = GatewayAuthFilter.class
                    .getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
            m.setAccessible(true);
            return (boolean) m.invoke(filter, req);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
