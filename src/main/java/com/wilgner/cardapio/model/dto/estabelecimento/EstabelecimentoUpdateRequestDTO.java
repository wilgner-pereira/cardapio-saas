package com.wilgner.cardapio.model.dto.estabelecimento;

import jakarta.validation.constraints.*;

// Request para atualizar dados do estabelecimento (proprietário)
public record EstabelecimentoUpdateRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        String nome,

        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        String descricao,

        @Size(max = 100, message = "Horário de funcionamento deve ter no máximo 100 caracteres")
        String horarioFuncionamento,

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        @Pattern(regexp = "^[0-9\\-\\s\\(\\)]*$", message = "Telefone inválido")
        String telefone,

        @Size(max = 255, message = "Endereço deve ter no máximo 255 caracteres")
        String endereco,

        @Size(max = 255, message = "Email de contato deve ter no máximo 255 caracteres")
        @Email(message = "Email de contato inválido")
        String emailContato
) {
}
