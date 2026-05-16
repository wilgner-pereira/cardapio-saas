package com.wilgner.cardapio.model.dto.estabelecimento;

import java.time.LocalDateTime;

// Response com dados do estabelecimento
public record EstabelecimentoResponseDTO(
        Long id,
        String nome,
        String slug,
        String descricao,
        String horarioFuncionamento,
        String telefone,
        String endereco,
        String emailContato,
        String logoUrl,
        Boolean ativo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
}
