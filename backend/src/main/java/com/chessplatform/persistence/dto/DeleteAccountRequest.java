package com.chessplatform.persistence.dto;

/** password: misma idea que en ChangePasswordRequest — borrar la cuenta es aún más destructivo. */
public record DeleteAccountRequest(String password) {
}