package com.chessplatform.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless con JWT, sin cookies de sesión
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/ws/**").permitAll()
                        .anyRequest().authenticated()
                );

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
}