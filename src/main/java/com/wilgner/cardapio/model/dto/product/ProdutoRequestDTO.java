package com.wilgner.cardapio.model.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProdutoRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 60, message = "Nome deve ter no mínimo 2 caracteres e no máximo 60")
        @Pattern(regexp = "^\\S(.|\\n)*\\S$", message = "Nome inválido")
        String nome,

        @NotBlank(message = "Descricao é obrigatório")
        @Size(min = 2, max = 300, message = "Descricao deve ter no mínimo 2 caracteres e no máximo 300")
        @Pattern(regexp = "^\\S(.|\\n)*\\S$", message = "Descricao inválido")
        String descricao,

        @NotNull(message = "Preço é obrigatório")
        @PositiveOrZero(message = "Preço não pode ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Preço deve ter no máximo 2 casas decimais")
        BigDecimal preco,

        @NotBlank(message = "Categoria é obrigatória")
        @Size(min = 2, max = 50, message = "Categoria deve ter no mínimo 2 caracteres e no máximo 50")
        @Pattern(regexp = "^[\\p{L}\\p{N} ._-]+$", message = "Categoria inválida")
        String categoria,

        @Size(max = 2048, message = "URL da imagem é muito longa")
        @Pattern(regexp = "^(|https?://.+)$", message = "URL da imagem inválida")
        String imageUrl

) {
}
