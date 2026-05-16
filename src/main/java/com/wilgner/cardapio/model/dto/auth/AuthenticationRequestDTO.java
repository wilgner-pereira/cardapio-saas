package com.wilgner.cardapio.model.dto.auth;

import jakarta.validation.constraints.*;

public record AuthenticationRequestDTO(
        @NotBlank(message = "O campo 'username' não pode estar em branco")
        @Size(min = 3, max = 255)
        String username,

        @NotBlank(message = "O campo 'password' não pode estar em branco")
        @Size(min = 8, max = 100)
        String password
) {
}