package com.chessplatform.bot;

/**
 * Los tres niveles iniciales — empezamos con estos tres y ampliamos más adelante, ver
 * conversación de diseño. Un enum con un valor por nivel, no un número directo de
 * habilidad en la API pública, para que añadir un nivel nuevo el día de mañana sea
 * añadir una constante más, no reajustar una escala continua ya en uso en partidas
 * pasadas y logros.
 *
 * skillLevel: el "Skill Level" de Stockfish, 0 (más débil) a 20 (fuerza completa).
 * moveTimeMs: cuánto tiempo "piensa" antes de responder — a igualdad de skillLevel, más
 * tiempo de cálculo da un juego algo más fuerte dentro de ese mismo nivel de habilidad.
 */
public enum BotDifficulty {
    EASY(2, 300),
    MEDIUM(10, 800),
    HARD(18, 1500);

    private final int skillLevel;
    private final int moveTimeMs;

    BotDifficulty(int skillLevel, int moveTimeMs) {
        this.skillLevel = skillLevel;
        this.moveTimeMs = moveTimeMs;
    }

    public int skillLevel() {
        return skillLevel;
    }

    public int moveTimeMs() {
        return moveTimeMs;
    }
}