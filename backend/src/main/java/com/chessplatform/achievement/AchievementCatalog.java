package com.chessplatform.achievement;

import com.chessplatform.rating.GameMode;

import java.util.List;

/**
 * Los logros de la plataforma, fijos en código — no hay tabla de "logros" en la base
 * de datos, porque las DEFINICIONES no cambian por usuario, solo su PROGRESO (que ni
 * eso se guarda: se calcula al vuelo, ver AchievementService). Añadir un logro nuevo es
 * añadir una línea aquí, sin migración de base de datos — así se amplió de los 20
 * iniciales a los que hay ahora, sin tocar nada de la infraestructura.
 */
public final class AchievementCatalog {

    public static final List<AchievementDefinition> ALL = List.of(
            // --- Generales: cuántas partidas has jugado en total ---
            new AchievementDefinition("primera-partida", "Primera partida",
                    "Juega tu primera partida", AchievementCategory.GENERAL, 1,
                    UserStatsSnapshot::gamesPlayed),
            new AchievementDefinition("aficionado", "Aficionado",
                    "Juega 10 partidas", AchievementCategory.GENERAL, 10,
                    UserStatsSnapshot::gamesPlayed),
            new AchievementDefinition("veterano", "Veterano",
                    "Juega 50 partidas", AchievementCategory.GENERAL, 50,
                    UserStatsSnapshot::gamesPlayed),
            new AchievementDefinition("incansable", "Incansable",
                    "Juega 200 partidas", AchievementCategory.GENERAL, 200,
                    UserStatsSnapshot::gamesPlayed),
            new AchievementDefinition("diplomatico", "Diplomático",
                    "Consigue 10 tablas", AchievementCategory.GENERAL, 10,
                    UserStatsSnapshot::gamesDrawn),

            // --- Victorias ---
            new AchievementDefinition("primera-victoria", "Primera victoria",
                    "Gana tu primera partida", AchievementCategory.VICTORIAS, 1,
                    UserStatsSnapshot::gamesWon),
            new AchievementDefinition("racha-ganadora", "Racha ganadora",
                    "Gana 10 partidas", AchievementCategory.VICTORIAS, 10,
                    UserStatsSnapshot::gamesWon),
            new AchievementDefinition("dominador", "Dominador",
                    "Gana 50 partidas", AchievementCategory.VICTORIAS, 50,
                    UserStatsSnapshot::gamesWon),
            new AchievementDefinition("verdugo", "Verdugo",
                    "Da jaque mate 5 veces", AchievementCategory.VICTORIAS, 5,
                    UserStatsSnapshot::checkmateWins),
            new AchievementDefinition("verdugo-experto", "Verdugo experto",
                    "Da jaque mate 25 veces", AchievementCategory.VICTORIAS, 25,
                    UserStatsSnapshot::checkmateWins),

            // --- Rating (el más alto alcanzado en cualquier modalidad) ---
            new AchievementDefinition("rating-1600", "Ascenso",
                    "Alcanza 1600 de rating en cualquier modalidad", AchievementCategory.RATING, 1600,
                    UserStatsSnapshot::highestRating),
            new AchievementDefinition("rating-1800", "Maestro en ciernes",
                    "Alcanza 1800 de rating en cualquier modalidad", AchievementCategory.RATING, 1800,
                    UserStatsSnapshot::highestRating),
            new AchievementDefinition("rating-2000", "Élite",
                    "Alcanza 2000 de rating en cualquier modalidad", AchievementCategory.RATING, 2000,
                    UserStatsSnapshot::highestRating),

            // --- Probar cada modalidad al menos una vez ---
            new AchievementDefinition("velocista", "Velocista",
                    "Juega una partida de bullet", AchievementCategory.MODALIDADES, 1,
                    s -> s.modesPlayed().contains(GameMode.BULLET) ? 1 : 0),
            new AchievementDefinition("relampago", "Relámpago",
                    "Juega una partida de blitz", AchievementCategory.MODALIDADES, 1,
                    s -> s.modesPlayed().contains(GameMode.BLITZ) ? 1 : 0),
            new AchievementDefinition("pensador", "Pensador",
                    "Juega una partida de rápidas", AchievementCategory.MODALIDADES, 1,
                    s -> s.modesPlayed().contains(GameMode.RAPID) ? 1 : 0),
            new AchievementDefinition("estratega", "Estratega",
                    "Juega una partida de clásicas", AchievementCategory.MODALIDADES, 1,
                    s -> s.modesPlayed().contains(GameMode.CLASSICAL) ? 1 : 0),

            // --- Social ---
            new AchievementDefinition("sociable", "Sociable",
                    "Añade a tu primer amigo", AchievementCategory.SOCIAL, 1,
                    UserStatsSnapshot::friendsCount),
            new AchievementDefinition("circulo-cercano", "Círculo cercano",
                    "Ten 10 amigos", AchievementCategory.SOCIAL, 10,
                    UserStatsSnapshot::friendsCount),
            new AchievementDefinition("charlatan", "Charlatán",
                    "Manda 50 mensajes directos", AchievementCategory.SOCIAL, 50,
                    UserStatsSnapshot::directMessagesSent),

            // --- Segunda tanda: niveles más altos de lo ya existente ---
            new AchievementDefinition("superviviente", "Superviviente",
                    "Juega 500 partidas", AchievementCategory.GENERAL, 500,
                    UserStatsSnapshot::gamesPlayed),
            new AchievementDefinition("leyenda", "Leyenda",
                    "Juega 1000 partidas", AchievementCategory.GENERAL, 1000,
                    UserStatsSnapshot::gamesPlayed),
            new AchievementDefinition("carnicero", "Carnicero",
                    "Da jaque mate 100 veces", AchievementCategory.VICTORIAS, 100,
                    UserStatsSnapshot::checkmateWins),
            new AchievementDefinition("invencible", "Invencible",
                    "Gana 200 partidas", AchievementCategory.VICTORIAS, 200,
                    UserStatsSnapshot::gamesWon),
            new AchievementDefinition("gran-maestro", "Gran Maestro",
                    "Alcanza 2200 de rating en cualquier modalidad", AchievementCategory.RATING, 2200,
                    UserStatsSnapshot::highestRating),
            new AchievementDefinition("superdotado", "Superdotado",
                    "Alcanza 2400 de rating en cualquier modalidad", AchievementCategory.RATING, 2400,
                    UserStatsSnapshot::highestRating),
            new AchievementDefinition("influencer", "Influencer",
                    "Ten 25 amigos", AchievementCategory.SOCIAL, 25,
                    UserStatsSnapshot::friendsCount),
            new AchievementDefinition("conversador", "Conversador",
                    "Manda 200 mensajes directos", AchievementCategory.SOCIAL, 200,
                    UserStatsSnapshot::directMessagesSent),

            // --- Aprender de la derrota y de las tablas menos frecuentes ---
            new AchievementDefinition("sabio-derrotado", "Sabio derrotado",
                    "Pierde 10 partidas — de los errores también se aprende", AchievementCategory.GENERAL, 10,
                    UserStatsSnapshot::gamesLost),
            new AchievementDefinition("ahogado", "Rey acorralado",
                    "Consigue tablas por ahogado", AchievementCategory.GENERAL, 1,
                    UserStatsSnapshot::stalemateDraws),

            // --- Probar las cuatro modalidades, no solo una ---
            new AchievementDefinition("todoterreno", "Todoterreno",
                    "Juega al menos una partida en las cuatro modalidades", AchievementCategory.MODALIDADES, 4,
                    s -> s.modesPlayed().size()),

            // --- Más social: quién te escribe a ti, y con cuánta gente distinta hablas ---
            new AchievementDefinition("popular", "Popular",
                    "Recibe 50 mensajes directos", AchievementCategory.SOCIAL, 50,
                    UserStatsSnapshot::directMessagesReceived),
            new AchievementDefinition("red-social", "Red social",
                    "Ten conversaciones con 5 amigos distintos", AchievementCategory.SOCIAL, 5,
                    UserStatsSnapshot::distinctConversationPartners),
            new AchievementDefinition("rompehielos", "Rompehielos",
                    "Manda tu primer mensaje directo", AchievementCategory.SOCIAL, 1,
                    UserStatsSnapshot::directMessagesSent),

            // --- Perfil ---
            new AchievementDefinition("imagen-personal", "Imagen personal",
                    "Pon un avatar en tu perfil", AchievementCategory.PERFIL, 1,
                    s -> s.hasAvatarSet() ? 1 : 0),
            new AchievementDefinition("de-donde-vienes", "De dónde vienes",
                    "Indica tu país en tu perfil", AchievementCategory.PERFIL, 1,
                    s -> s.hasCountrySet() ? 1 : 0),
            new AchievementDefinition("veterano-de-guerra", "Veterano de guerra",
                    "Cumple 30 días desde que te registraste", AchievementCategory.PERFIL, 30,
                    UserStatsSnapshot::accountAgeDays),
            new AchievementDefinition("fiel-seguidor", "Fiel seguidor",
                    "Cumple 365 días desde que te registraste", AchievementCategory.PERFIL, 365,
                    UserStatsSnapshot::accountAgeDays)
    );

    private AchievementCatalog() {
    }
}