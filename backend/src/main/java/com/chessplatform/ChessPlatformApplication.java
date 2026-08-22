package com.chessplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // necesario para el tick de matchmaking (ver MatchmakingService)
@EnableAsync // necesario para generar puzzles en segundo plano tras cada partida, ver puzzle.PuzzleGenerationService y AsyncConfig
public class ChessPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessPlatformApplication.class, args);
    }
}