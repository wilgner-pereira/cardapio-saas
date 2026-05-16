package com.wilgner.cardapio.model.dto.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProdutoOrdemRequestDTO(
        @NotNull(message = "Ordem é obrigatória")
        @Min(value = 0, message = "Ordem não pode ser negativa")
        Integer ordem
) {
}
