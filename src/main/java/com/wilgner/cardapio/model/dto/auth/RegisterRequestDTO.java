package com.wilgner.cardapio.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank(message = "O campo 'username' não pode estar em branco")
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Username inválido")
        String username,

        @NotBlank(message = "O campo 'password' não pode estar em branco")
        @Size(min = 8, max = 100)
        String password,


        @NotBlank
        @Email
        String email
) {}
