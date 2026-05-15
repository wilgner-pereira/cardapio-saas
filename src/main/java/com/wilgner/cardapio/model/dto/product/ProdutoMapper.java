package com.wilgner.cardapio.model.dto.product;

import com.wilgner.cardapio.model.entity.Produto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProdutoMapper {
    public ProdutoResponseDTO toDTO(Produto produto) {
        if (produto == null) {
            return null;
        }

        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.getCategoria(),
                produto.getImagemUrl(),
                produto.isAtivo()
        );
    }

    public List<ProdutoResponseDTO> toDTOList(List<Produto> produtos) {
        if (produtos == null) {
            return List.of();
        }

        return produtos.stream()
                .map(this::toDTO)
                .toList();
    }
}
