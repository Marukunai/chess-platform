package com.chessplatform.auth.config;

import com.chessplatform.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // El SecurityContext es estático (ThreadLocal) — limpiarlo evita que un test
        // deje autenticado al siguiente por error.
        SecurityContextHolder.clearContext();
    }

    @Test
    void populatesSecurityContextWhenTheTokenIsValid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUserId("valid-token")).thenReturn("user-123");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user-123");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void leavesSecurityContextEmptyWhenThereIsNoAuthorizationHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response); // la petición sigue adelante igualmente
    }

    @Test
    void leavesSecurityContextEmptyButStillContinuesTheChainWhenTheTokenIsInvalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer garbage-token");
        when(jwtService.extractUserId("garbage-token")).thenThrow(new RuntimeException("token corrupto"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response); // nunca se corta la petición aquí, ver javadoc de la clase
    }

    @Test
    void ignoresAuthorizationHeadersThatAreNotBearerTokens() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}