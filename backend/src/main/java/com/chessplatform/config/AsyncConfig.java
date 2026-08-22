package com.chessplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * El grupo de hilos que usa @Async en toda la aplicación — hoy solo lo usa
 * PuzzleGenerationService, pero está pensado para cualquier trabajo futuro que deba
 * correr en segundo plano sin bloquear el hilo que atiende la petición o el mensaje
 * STOMP que lo dispara.
 *
 * Acotado a propósito (2 hilos, cola de 50) en vez del ejecutor por defecto de Spring
 * (uno nuevo por tarea, sin límite): analizar una partida entera con Stockfish tarda
 * varios segundos, y sin límite, un pico de partidas terminando a la vez podría
 * arrancar decenas de procesos de Stockfish simultáneos — nada bueno para la memoria
 * del servidor. Con solo 2 hilos, como mucho hay 2 análisis en curso a la vez; el resto
 * espera en la cola, sin perderse (hasta 50 partidas pendientes de analizar).
 */
@Configuration
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
}