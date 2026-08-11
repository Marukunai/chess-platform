package com.chessplatform.matchmaking;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Empareja jugadores de la cola cada pocos segundos, ampliando la ventana de tolerancia
 * de rating cuanto más tiempo lleva alguien esperando (versión simplificada del seek
 * graph de Lichess).
 */
@Service
public class MatchmakingService {

    private static final int INITIAL_RATING_WINDOW = 100;
    private static final int WINDOW_GROWTH_PER_TICK = 50;

    private final MatchmakingQueue queue;

    public MatchmakingService(MatchmakingQueue queue) {
        this.queue = queue;
    }

    @Scheduled(fixedRate = 2000)
    public void tick() {
        // TODO (Fase 1): recorrer la cola, emparejar jugadores dentro de la ventana de
        // rating (ampliándola con el tiempo de espera), crear GameSession para cada
        // pareja y notificarles vía /topic/matchmaking/{playerId}.
    }
}
