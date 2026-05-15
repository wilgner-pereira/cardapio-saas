package com.wilgner.cardapio.model.dto.product;

import jakarta.validation.constraints.NotNull;

public record ProdutoStatusRequestDTO(
        @NotNull(message = "Status é obrigatório")
        Boolean ativo
) {
}
