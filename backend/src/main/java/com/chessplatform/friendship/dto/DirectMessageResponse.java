package com.chessplatform.friendship.dto;

public record DirectMessageResponse(String id, String senderUserId, String text, String sentAt) {
}