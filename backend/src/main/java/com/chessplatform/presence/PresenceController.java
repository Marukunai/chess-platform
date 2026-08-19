package com.chessplatform.presence;

import com.chessplatform.presence.dto.SetDoNotDisturbMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @MessageMapping("/presence/dnd")
    public void setDoNotDisturb(SetDoNotDisturbMessage message, Principal principal) {
        if (principal == null) {
            return;
        }
        presenceService.setDoNotDisturbAndNotifyFriends(principal.getName(), message.enabled());
    }
}