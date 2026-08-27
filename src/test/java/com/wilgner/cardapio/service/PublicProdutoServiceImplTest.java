package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicProdutoServiceImplTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;

    @Mock
    private ProdutoMapper produtoMapper;

    @InjectMocks
    private PublicProdutoServiceImpl publicProdutoService;

    private Estabelecimento estabelecimentoAtivo;
    private Estabelecimento estabelecimentoInativo;
    private Produto produto1;
    private ProdutoResponseDTO produtoResponseDTO1;

    @BeforeEach
    void setUp() {
        estabelecimentoAtivo = new Estabelecimento();
        estabelecimentoAtivo.setId(1L);
        estabelecimentoAtivo.setSlug("loja-teste");
        estabelecimentoAtivo.setNome("Loja Teste");
        estabelecimentoAtivo.setAtivo(true);
        estabelecimentoAtivo.setDescricao("Descricao");
        estabelecimentoAtivo.setLogoUrl("logo.png");
        estabelecimentoAtivo.setHorarioFuncionamento("08:00 - 18:00");
        estabelecimentoAtivo.setTelefone("123456789");
        estabelecimentoAtivo.setEndereco("Rua Teste");
        estabelecimentoAtivo.setEmailContato("contato@teste.com");

        estabelecimentoInativo = new Estabelecimento();
        estabelecimentoInativo.setId(2L);
        estabelecimentoInativo.setSlug("loja-inativa");
        estabelecimentoInativo.setAtivo(false);

        produto1 = new Produto();
        produto1.setId(1L);
        produto1.setNome("Produto 1");
        produto1.setCategoria("Categoria 1");

        produtoResponseDTO1 = new ProdutoResponseDTO(
                1L, "Produto 1", "Desc", BigDecimal.TEN, "Categoria 1", "img.png", true, 0
        );
    }

    // --- Tests for listarProdutoDinamico ---

    @Test
    void listarProdutoDinamico_ThrowsException_WhenEstabelecimentoNotFound() {
        when(estabelecimentoRepository.findBySlug("nao-existe")).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            publicProdutoService.listarProdutoDinamico("nao-existe", null);
        });

        assertEquals("Estabelecimento não encontrado", exception.getMessage());
        verify(produtoRepository, never()).findByEstabelecimentoCategoriaAtivoOrderByOrdem(any(), any());
        verify(produtoRepository, never()).findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(any());
    }

    @Test
    void listarProdutoDinamico_ThrowsException_WhenEstabelecimentoInactive() {
        when(estabelecimentoRepository.findBySlug("loja-inativa")).thenReturn(Optional.of(estabelecimentoInativo));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            publicProdutoService.listarProdutoDinamico("loja-inativa", null);
        });

        assertEquals("Estabelecimento desativado", exception.getMessage());
        verify(produtoRepository, never()).findByEstabelecimentoCategoriaAtivoOrderByOrdem(any(), any());
        verify(produtoRepository, never()).findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(any());
    }

    @Test
    void listarProdutoDinamico_ReturnsProductsFilteredByCategory_WhenCategoriaIsProvided() {
        String slug = "loja-teste";
        String categoria = "Categoria 1";
        List<Produto> produtos = List.of(produto1);
        List<ProdutoResponseDTO> responseDTOs = List.of(produtoResponseDTO1);

        when(estabelecimentoRepository.findBySlug(slug)).thenReturn(Optional.of(estabelecimentoAtivo));
        when(produtoRepository.findByEstabelecimentoCategoriaAtivoOrderByOrdem(estabelecimentoAtivo, categoria))
                .thenReturn(produtos);
        when(produtoMapper.toDTOList(produtos)).thenReturn(responseDTOs);

        List<ProdutoResponseDTO> result = publicProdutoService.listarProdutoDinamico(slug, categoria);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(responseDTOs, result);

        verify(produtoRepository).findByEstabelecimentoCategoriaAtivoOrderByOrdem(estabelecimentoAtivo, categoria);
        verify(produtoRepository, never()).findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(any());
    }

    @Test
    void listarProdutoDinamico_ReturnsProductsFilteredByCategoryTrimmed_WhenCategoriaHasWhitespace() {
        String slug = "loja-teste";
        String categoria = "  Categoria 1  ";
        List<Produto> produtos = List.of(produto1);
        List<ProdutoResponseDTO> responseDTOs = List.of(produtoResponseDTO1);

        when(estabelecimentoRepository.findBySlug(slug)).thenReturn(Optional.of(estabelecimentoAtivo));
        when(produtoRepository.findByEstabelecimentoCategoriaAtivoOrderByOrdem(estabelecimentoAtivo, "Categoria 1"))
                .thenReturn(produtos);
        when(produtoMapper.toDTOList(produtos)).thenReturn(responseDTOs);

        List<ProdutoResponseDTO> result = publicProdutoService.listarProdutoDinamico(slug, categoria);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(produtoRepository).findByEstabelecimentoCategoriaAtivoOrderByOrdem(estabelecimentoAtivo, "Categoria 1");
    }

    @Test
    void listarProdutoDinamico_ReturnsAllProducts_WhenCategoriaIsEmpty() {
        String slug = "loja-teste";
        String categoria = "";
        List<Produto> produtos = List.of(produto1);
        List<ProdutoResponseDTO> responseDTOs = List.of(produtoResponseDTO1);

        when(estabelecimentoRepository.findBySlug(slug)).thenReturn(Optional.of(estabelecimentoAtivo));
        when(produtoRepository.findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(estabelecimentoAtivo))
                .thenReturn(produtos);
        when(produtoMapper.toDTOList(produtos)).thenReturn(responseDTOs);

        List<ProdutoResponseDTO> result = publicProdutoService.listarProdutoDinamico(slug, categoria);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(responseDTOs, result);

        verify(produtoRepository).findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(estabelecimentoAtivo);
        verify(produtoRepository, never()).findByEstabelecimentoCategoriaAtivoOrderByOrdem(any(), any());
    }

    @Test
    void listarProdutoDinamico_ReturnsAllProducts_WhenCategoriaIsNull() {
        String slug = "loja-teste";
        String categoria = null;
        List<Produto> produtos = List.of(produto1);
        List<ProdutoResponseDTO> responseDTOs = List.of(produtoResponseDTO1);

        when(estabelecimentoRepository.findBySlug(slug)).thenReturn(Optional.of(estabelecimentoAtivo));
        when(produtoRepository.findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(estabelecimentoAtivo))
                .thenReturn(produtos);
        when(produtoMapper.toDTOList(produtos)).thenReturn(responseDTOs);

        List<ProdutoResponseDTO> result = publicProdutoService.listarProdutoDinamico(slug, categoria);

        assertNotNull(result);
        assertEquals(1, result.size());

        verify(produtoRepository).findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(estabelecimentoAtivo);
        verify(produtoRepository, never()).findByEstabelecimentoCategoriaAtivoOrderByOrdem(any(), any());
    }

    // --- Tests for obterInfoEstabelecimento ---

    @Test
    void obterInfoEstabelecimento_ThrowsException_WhenEstabelecimentoNotFound() {
        when(estabelecimentoRepository.findBySlug("nao-existe")).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            publicProdutoService.obterInfoEstabelecimento("nao-existe");
        });

        assertEquals("Estabelecimento não encontrado", exception.getMessage());
    }

    @Test
    void obterInfoEstabelecimento_ThrowsException_WhenEstabelecimentoInactive() {
        when(estabelecimentoRepository.findBySlug("loja-inativa")).thenReturn(Optional.of(estabelecimentoInativo));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            publicProdutoService.obterInfoEstabelecimento("loja-inativa");
        });

        assertEquals("Estabelecimento desativado", exception.getMessage());
    }

    @Test
    void obterInfoEstabelecimento_ReturnsEstabelecimentoInfoDTO_WhenFoundAndActive() {
        String slug = "loja-teste";
        when(estabelecimentoRepository.findBySlug(slug)).thenReturn(Optional.of(estabelecimentoAtivo));

        PublicProdutoServiceImpl.EstabelecimentoInfoDTO result = publicProdutoService.obterInfoEstabelecimento(slug);

        assertNotNull(result);
        assertEquals("Loja Teste", result.nome());
        assertEquals("Descricao", result.descricao());
        assertEquals("logo.png", result.logoUrl());
        assertEquals("08:00 - 18:00", result.horarioFuncionamento());
        assertEquals("123456789", result.telefone());
        assertEquals("Rua Teste", result.endereco());
        assertEquals("contato@teste.com", result.emailContato());
    }
}
