package com.chessplatform.auth.config;

import com.chessplatform.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida el JWT en peticiones HTTP normales — el CONNECT de STOMP ya tiene su propia
 * validación aparte (ver StompAuthChannelInterceptor), esto es solo para HTTP.
 *
 * Puebla el SecurityContext si el token es válido, pero NUNCA rechaza la petición aquí
 * mismo: quien decide si una ruta necesita estar autenticada es SecurityConfig
 * (.authenticated() vs .permitAll()), este filtro solo aporta la identidad cuando la
 * hay. Sin token, o con uno inválido/caducado, la petición sigue adelante sin
 * autenticar — y si la ruta lo exigía, Spring Security la rechaza más adelante en la
 * cadena con un 401 normal, no aquí con algo más críptico.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                String userId = jwtService.extractUserId(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (RuntimeException e) {
                // Token mal formado, caducado o firmado con otra clave — se ignora, ver
                // el javadoc de la clase: aquí nunca se rechaza la petición.
            }
        }
        filterChain.doFilter(request, response);
    }
}