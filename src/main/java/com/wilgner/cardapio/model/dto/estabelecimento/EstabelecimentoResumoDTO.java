package com.wilgner.cardapio.model.dto.estabelecimento;

// Response resumida para listagem
public record EstabelecimentoResumoDTO(
        Long id,
        String nome,
        String slug,
        String descricao,
        String endereco,
        String logoUrl,
        Boolean ativo
) {
}
