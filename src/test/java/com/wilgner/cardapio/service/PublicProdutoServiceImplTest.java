package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProdutoServiceImplTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;

    @Mock
    private ProdutoMapper produtoMapper;

    @InjectMocks
    private PublicProdutoServiceImpl service;

    @Test
    void listarProdutoDinamico_ThrowsResourceNotFoundException_WhenEstabelecimentoIsInactive() {
        // Arrange
        String slug = "meu-estabelecimento";
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setSlug(slug);
        estabelecimento.setAtivo(false);

        when(estabelecimentoRepository.findBySlug(slug)).thenReturn(Optional.of(estabelecimento));

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            service.listarProdutoDinamico(slug, null);
        });

        assertEquals("Estabelecimento desativado", exception.getMessage());
    }
}
