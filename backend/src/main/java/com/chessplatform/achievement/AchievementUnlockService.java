package com.chessplatform.achievement;

import com.chessplatform.achievement.dto.AchievementUnlockedMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserAchievementUnlock;
import com.chessplatform.persistence.repository.UserAchievementUnlockRepository;
import com.chessplatform.persistence.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El único sitio donde un logro pasa de "sin desbloquear" a "desbloqueado, con fecha" —
 * checkAndNotify() se llama después de CUALQUIER evento que pudiera cambiar el
 * progreso de alguien (terminar una partida, aceptar una amistad, mandar un mensaje,
 * editar el perfil), y es quien decide si hay algo nuevo que guardar y avisar.
 *
 * A propósito NO se llama en ningún sitio de lectura (ver una partida, un perfil, el
 * ranking) — llamarlo ahí detectaría el desbloqueo "cuando alguien mira", no "cuando
 * pasa de verdad", y eso habría hecho inútil tanto el aviso en directo (llegaría tarde,
 * o nunca si no vuelves a mirar) como "quién fue el primero" (la fecha sería de cuando
 * se comprobó, no de cuando se consiguió).
 */
@Component
public class AchievementUnlockService {

    private final AchievementService achievementService;
    private final UserAchievementUnlockRepository unlockRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public AchievementUnlockService(AchievementService achievementService, UserAchievementUnlockRepository unlockRepository,
                                    UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.achievementService = achievementService;
        this.unlockRepository = unlockRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void checkAndNotify(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return; // no debería pasar en la práctica — solo se llama con ids de sesiones autenticadas de verdad
        }

        List<AchievementService.AchievementProgress> progress = achievementService.progressFor(userId);
        Set<String> alreadyUnlockedIds = unlockRepository.findByUser_Id(userId).stream()
                .map(UserAchievementUnlock::getAchievementId)
                .collect(Collectors.toSet());

        for (AchievementService.AchievementProgress p : progress) {
            if (p.unlocked() && !alreadyUnlockedIds.contains(p.definition().id())) {
                unlockRepository.save(new UserAchievementUnlock(user, p.definition().id()));
                messagingTemplate.convertAndSend(
                        "/topic/user/%s".formatted(userId),
                        new AchievementUnlockedMessage(p.definition().id(), p.definition().name(), p.definition().description())
                );
            }
        }
    }
}