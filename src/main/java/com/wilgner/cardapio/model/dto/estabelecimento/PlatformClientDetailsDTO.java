package com.wilgner.cardapio.model.dto.estabelecimento;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record PlatformClientDetailsDTO(
        @JsonUnwrapped EstabelecimentoResponseDTO estabelecimento,
        String username,
        String email
) {}
