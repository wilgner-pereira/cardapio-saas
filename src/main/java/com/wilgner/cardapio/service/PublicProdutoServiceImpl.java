package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
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

    @Transactional
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

    @Transactional
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
                estabelecimento.getHorarioFuncionamento(),
                estabelecimento.getTelefone(),
                estabelecimento.getEndereco(),
                estabelecimento.getEmailContato()
        );
    }

    public record EstabelecimentoInfoDTO(
            String nome,
            String descricao,
            String logoUrl,
            String horarioFuncionamento,
            String telefone,
            String endereco,
            String emailContato
    ) {}
}
