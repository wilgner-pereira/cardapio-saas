package com.wilgner.cardapio.model.dto.auth;

public record AuthenticationResponseDTO(String message, String accessToken, String tokenType) {
}
