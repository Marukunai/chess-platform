package com.chessplatform.persistence.dto;

/** country/avatarUrl: opcionales, null o vacío se guarda tal cual como "sin fijar". */
public record UpdateProfileRequest(String username, String country, String avatarUrl) {
}