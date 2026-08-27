package com.wilgner.cardapio.model.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoriaOrdemRequestDTO(
        @NotBlank(message = "Categoria é obrigatória")
        @Size(min = 2, max = 50, message = "Categoria deve ter no mínimo 2 caracteres e no máximo 50")
        @Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "Categoria inválida")
        String categoria,

        @NotBlank(message = "Categoria alvo é obrigatória")
        @Size(min = 2, max = 50, message = "Categoria alvo deve ter no mínimo 2 caracteres e no máximo 50")
        @Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "Categoria alvo inválida")
        String categoriaAlvo
) {
}
