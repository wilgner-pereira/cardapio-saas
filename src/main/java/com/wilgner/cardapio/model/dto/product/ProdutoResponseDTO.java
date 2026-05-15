package com.wilgner.cardapio.model.dto.product;

import java.math.BigDecimal;

public record ProdutoResponseDTO(Long id, String nome, String descricao, BigDecimal preco, String categoria, String imageUrl, boolean ativo) {
}
