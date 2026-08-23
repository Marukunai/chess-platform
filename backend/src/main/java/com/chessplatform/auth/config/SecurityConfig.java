package com.chessplatform.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(CorsProperties corsProperties, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.corsProperties = corsProperties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
                        .requestMatchers("/api/auth/**", "/ws/**", "/health", "/error").permitAll()
                        // Solo LECTURA es pública en /api/games/**, /api/users/** y
                        // /api/achievements/** — revisar partidas, perfiles o el
                        // progreso de logros (propios o ajenos) es normal en cualquier
                        // plataforma de ajedrez real, igual que el ranking de rating o
                        // el ranking global de logros. Escribir (editar tu propio
                        // perfil) sí necesita identidad de verdad, por eso
                        // JwtAuthenticationFilter ya hacía falta — ver su javadoc y el
                        // de más abajo.
                        .requestMatchers(HttpMethod.GET, "/api/games/**", "/api/users/**", "/api/achievements/**").permitAll()
                        // Ranking de puzzles, público igual que los de arriba — pero
                        // solo esta ruta en concreto, no todo /api/puzzles/**: /next y
                        // /attempt sí necesitan identidad de verdad (quién eres decide
                        // qué te toca ver y actualiza TU rating), ver PuzzleController.
                        .requestMatchers(HttpMethod.GET, "/api/puzzles/leaderboard").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // /health es el endpoint que usa el healthcheck del proveedor de hosting
        // (Render) para saber si el backend está vivo — tiene que ser accesible sin
        // autenticar, nadie va a mandarle un JWT.
        //
        // /error: cuando cualquier controlador lanza una excepción (incluidas las
        // ResponseStatusException que lanzamos nosotros a propósito, como usuario ya
        // existente o credenciales incorrectas), Spring Boot hace un forward interno a
        // /error para construir la respuesta. Si esa ruta no es pública, Spring Security
        // la bloquea con un 403 genérico que tapa el status/mensaje real — así que tiene
        // que ser tan pública como cualquier otra ruta a la que ya se pueda llegar sin
        // autenticar.
        //
        // /ws/** queda público a propósito a nivel HTTP: el handshake de WebSocket no
        // puede llevar cabecera Authorization (los navegadores no lo permiten en la
        // petición de upgrade). La identidad real se valida más abajo, en el propio
        // frame STOMP CONNECT — ver StompAuthChannelInterceptor en realtime/config.

        return http.build();
    }

    /**
     * El cliente web se sirve desde un origen distinto al backend (p. ej.
     * localhost:5500 vs localhost:8080, o los dos dominios .onrender.com en producción)
     * — sin esto, el navegador bloquea las llamadas a /api/auth/** antes de que lleguen
     * al servidor. Los orígenes concretos salen de CorsProperties (app.cors.allowed-origins),
     * configurable por variable de entorno — ver ADR de despliegue en
     * docs/architecture-decisions.md.
     *
     * allowCredentials(true) + una lista explícita de cabeceras (nunca "*") a propósito:
     * SockJS manda sus peticiones de sondeo (/ws/info) con withCredentials=true por
     * diseño propio, así que el servidor tiene que responder con
     * Access-Control-Allow-Credentials: true — y esa cabecera es incompatible con
     * Access-Control-Allow-Headers: "*" según la propia especificación CORS (Spring lo
     * valida y rechaza la petición si se combinan mal, que es justo lo que pasaba
     * antes).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-Requested-With", "Accept"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}