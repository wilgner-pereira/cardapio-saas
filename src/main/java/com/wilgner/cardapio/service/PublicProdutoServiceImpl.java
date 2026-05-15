package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.ProdutoRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PublicProdutoServiceImpl {
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoMapper produtoMapper;
    public PublicProdutoServiceImpl(ProdutoRepository produtoRepository, UsuarioRepository usuarioRepository, ProdutoMapper produtoMapper) {
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.produtoMapper = produtoMapper;
    }

    @Transactional
    public List<ProdutoResponseDTO> listarProdutoDinamico(String name, String categoria) {

        Usuario usuario = usuarioRepository.findByUsername(name)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if(StringUtils.hasText(categoria)) {
            List<Produto> produtos = produtoRepository.findByUsuarioAndCategoriaAndAtivo(usuario, categoria.trim(), true);
            return produtoMapper.toDTOList(produtos);
        }
        return produtoMapper.toDTOList(produtoRepository.findByUsuarioAndAtivo(usuario, true));
    }


}
