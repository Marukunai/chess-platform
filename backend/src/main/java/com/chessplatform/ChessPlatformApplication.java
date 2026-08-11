package com.chessplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // necesario para el tick de matchmaking (ver MatchmakingService)
public class ChessPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChessPlatformApplication.class, args);
    }
}
