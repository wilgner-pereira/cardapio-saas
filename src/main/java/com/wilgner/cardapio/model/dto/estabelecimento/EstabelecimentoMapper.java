package com.wilgner.cardapio.model.dto.estabelecimento;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EstabelecimentoMapper {

    public EstabelecimentoResponseDTO toDTO(Estabelecimento estabelecimento) {
        if (estabelecimento == null) {
            return null;
        }

        return new EstabelecimentoResponseDTO(
                estabelecimento.getId(),
                estabelecimento.getNome(),
                estabelecimento.getSlug(),
                estabelecimento.getDescricao(),
                estabelecimento.getHorarioFuncionamento(),
                estabelecimento.getTelefone(),
                estabelecimento.getEndereco(),
                estabelecimento.getEmailContato(),
                estabelecimento.getLogoUrl(),
                estabelecimento.getAtivo(),
                estabelecimento.getCriadoEm(),
                estabelecimento.getAtualizadoEm()
        );
    }

    public EstabelecimentoResumoDTO toResumoDTO(Estabelecimento estabelecimento) {
        if (estabelecimento == null) {
            return null;
        }

        return new EstabelecimentoResumoDTO(
                estabelecimento.getId(),
                estabelecimento.getNome(),
                estabelecimento.getSlug(),
                estabelecimento.getDescricao(),
                estabelecimento.getEndereco(),
                estabelecimento.getLogoUrl(),
                estabelecimento.getAtivo()
        );
    }

    public List<EstabelecimentoResponseDTO> toDTOList(List<Estabelecimento> estabelecimentos) {
        if (estabelecimentos == null) {
            return List.of();
        }

        return estabelecimentos.stream()
                .map(this::toDTO)
                .toList();
    }

    public List<EstabelecimentoResumoDTO> toResumoDTOList(List<Estabelecimento> estabelecimentos) {
        if (estabelecimentos == null) {
            return List.of();
        }

        return estabelecimentos.stream()
                .map(this::toResumoDTO)
                .toList();
    }

    public Estabelecimento toEntity(EstabelecimentoCreateRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setNome(dto.nome().trim());
        estabelecimento.setDescricao(dto.descricao());
        estabelecimento.setHorarioFuncionamento(dto.horarioFuncionamento());
        estabelecimento.setTelefone(dto.telefone());
        estabelecimento.setEndereco(dto.endereco());
        estabelecimento.setEmailContato(dto.emailContato());
        estabelecimento.setAtivo(true);

        return estabelecimento;
    }

    public void updateEntity(EstabelecimentoUpdateRequestDTO dto, Estabelecimento estabelecimento) {
        if (dto == null || estabelecimento == null) {
            return;
        }

        estabelecimento.setNome(dto.nome().trim());
        estabelecimento.setDescricao(dto.descricao());
        estabelecimento.setHorarioFuncionamento(dto.horarioFuncionamento());
        estabelecimento.setTelefone(dto.telefone());
        estabelecimento.setEndereco(dto.endereco());
        estabelecimento.setEmailContato(dto.emailContato());
    }
}
