package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PublicProdutoServiceImpl {
    private final ProdutoRepository produtoRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final ProdutoMapper produtoMapper;

    public PublicProdutoServiceImpl(ProdutoRepository produtoRepository,
                                    EstabelecimentoRepository estabelecimentoRepository,
                                    ProdutoMapper produtoMapper) {
        this.produtoRepository = produtoRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.produtoMapper = produtoMapper;
    }

    // ⚡ Bolt Optimization: Use Spring @Transactional(readOnly = true) instead of jakarta.transaction.Transactional.
    // This sets Hibernate flush mode to MANUAL (disabling dirty checking overhead across loaded entities)
    // and configures the JDBC connection as read-only, optimizing public menu query performance and memory usage.
    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarProdutoDinamico(String slug, String categoria) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));

        if (!estabelecimento.getAtivo()) {
            throw new ResourceNotFoundException("Estabelecimento desativado");
        }

        if (StringUtils.hasText(categoria)) {
            List<Produto> produtos = produtoRepository.findByEstabelecimentoCategoriaAtivoOrderByOrdem(
                    estabelecimento, categoria.trim()
            );
            return produtoMapper.toDTOList(produtos);
        }

        List<Produto> produtos = produtoRepository.findByEstabelecimentoAtivoOrderByCategoriaAndOrdem(
                estabelecimento
        );
        return produtoMapper.toDTOList(produtos);
    }

    // ⚡ Bolt Optimization: Use Spring @Transactional(readOnly = true) for read-only establishment info retrieval.
    @Transactional(readOnly = true)
    public EstabelecimentoInfoDTO obterInfoEstabelecimento(String slug) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));

        if (!estabelecimento.getAtivo()) {
            throw new ResourceNotFoundException("Estabelecimento desativado");
        }

        return new EstabelecimentoInfoDTO(
                estabelecimento.getNome(),
                estabelecimento.getDescricao(),
                estabelecimento.getLogoUrl(),
                estabelecimento.getHeroImageUrl(),
                estabelecimento.getInstagramUrl(),
                estabelecimento.getWhatsapp(),
                estabelecimento.getHorarioFuncionamento(),
                estabelecimento.getTelefone(),
                estabelecimento.getEndereco(),
                estabelecimento.getEmailContato(),
                estabelecimento.getTema(),
                estabelecimento.isImagensRedondas()
        );
    }

    public record EstabelecimentoInfoDTO(
            String nome,
            String descricao,
            String logoUrl,
            String heroImageUrl,
            String instagramUrl,
            String whatsapp,
            String horarioFuncionamento,
            String telefone,
            String endereco,
            String emailContato,
            String tema,
            boolean imagensRedondas
    ) {}
}
