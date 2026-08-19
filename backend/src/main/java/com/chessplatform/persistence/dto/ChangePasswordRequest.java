package com.chessplatform.persistence.dto;

/** currentPassword: obligatoria — nadie cambia una contraseña sin demostrar que ya la conoce. */
public record ChangePasswordRequest(String currentPassword, String newPassword) {
}