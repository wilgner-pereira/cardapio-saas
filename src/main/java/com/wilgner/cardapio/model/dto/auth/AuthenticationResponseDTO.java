package com.wilgner.cardapio.model.dto.auth;

import java.util.List;

public record AuthenticationResponseDTO(
        String message,
        String accessToken,
        String tokenType,
        Long userId,
        String username,
        String email,
        String estabelecimentoSlug,
        List<String> roles,
        long accessExpiresInMs
) {
}
