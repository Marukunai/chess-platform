package com.chessplatform.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // API stateless con JWT, sin cookies de sesión
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/games/**", "/ws/**", "/health").permitAll()
                        .anyRequest().authenticated()
                );

        // /api/games/** es de lectura pública a propósito: revisar partidas (propias o
        // ajenas) es normal en cualquier plataforma de ajedrez real (lichess,
        // chess.com), y así tampoco depende de que exista JwtAuthenticationFilter
        // todavía (sigue pendiente, ver nota más abajo).
        //
        // /health es el endpoint que usa el healthcheck del proveedor de hosting
        // (Render) para saber si el backend está vivo — tiene que ser accesible sin
        // autenticar, nadie va a mandarle un JWT.
        //
        // /ws/** queda público a propósito a nivel HTTP: el handshake de WebSocket no
        // puede llevar cabecera Authorization (los navegadores no lo permiten en la
        // petición de upgrade). La identidad real se valida más abajo, en el propio
        // frame STOMP CONNECT — ver StompAuthChannelInterceptor en realtime/config.
        //
        // TODO: cuando exista el primer endpoint REST más allá de /api/auth/** que
        // necesite identidad (perfil, historial de partidas...), añadir aquí un
        // JwtAuthenticationFilter clásico antes de UsernamePasswordAuthenticationFilter.
        // No lo añado todavía porque no habría ningún endpoint que lo necesitara.

        return http.build();
    }

    /**
     * El cliente web se sirve desde un origen distinto al backend (p. ej.
     * localhost:5500 vs localhost:8080, o los dos dominios .onrender.com en producción)
     * — sin esto, el navegador bloquea las llamadas a /api/auth/** antes de que lleguen
     * al servidor. Los orígenes concretos salen de CorsProperties (app.cors.allowed-origins),
     * configurable por variable de entorno — ver ADR de despliegue en
     * docs/architecture-decisions.md.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}