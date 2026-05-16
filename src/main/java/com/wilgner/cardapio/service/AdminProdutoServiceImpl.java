package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoRequestDTO;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.ProdutoRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminProdutoServiceImpl {
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoMapper produtoMapper;

    public AdminProdutoServiceImpl(ProdutoRepository produtoRepository, UsuarioRepository usuarioRepository, ProdutoMapper produtoMapper) {
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.produtoMapper = produtoMapper;
    }

    @Transactional
    public ProdutoResponseDTO criarProduto(ProdutoRequestDTO produtoRequestDTO) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();

        Produto produto = new Produto();
        produto.setEstabelecimento(estabelecimento);
        produto.setUsuario(usuario);
        produto.setNome(produtoRequestDTO.nome().trim());
        produto.setDescricao(produtoRequestDTO.descricao().trim());
        produto.setPreco(produtoRequestDTO.preco());
        produto.setCategoria(produtoRequestDTO.categoria().trim());
        produto.setImagemUrl(produtoRequestDTO.imageUrl());
        produto.setOrdem(proximaOrdem(estabelecimento, produto.getCategoria()));

        Produto salvo = produtoRepository.save(produto);
        return produtoMapper.toDTO(salvo);
    }

    @Transactional
    public ProdutoResponseDTO atualizarProduto(ProdutoRequestDTO produtoRequestDTO, Long produtoId) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);

        produto.setNome(produtoRequestDTO.nome().trim());
        produto.setDescricao(produtoRequestDTO.descricao().trim());
        produto.setPreco(produtoRequestDTO.preco());
        produto.setCategoria(produtoRequestDTO.categoria().trim());
        produto.setImagemUrl(produtoRequestDTO.imageUrl());

        Produto salvo = produtoRepository.save(produto);
        return produtoMapper.toDTO(salvo);
    }

    @Transactional
    public void deleteProduto(Long produtoId) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);

        produtoRepository.delete(produto);
    }

    @Transactional
    public ProdutoResponseDTO atualizarStatus(Long produtoId, boolean ativo) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);
        produto.setAtivo(ativo);
        return produtoMapper.toDTO(produtoRepository.save(produto));
    }

    @Transactional
    public ProdutoResponseDTO pesquisarProdutoPorId(Long produtoId) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);
        return produtoMapper.toDTO(produto);
    }

    @Transactional
    public List<ProdutoResponseDTO> listarProdutoDinamico(String categoria) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();

        if (StringUtils.hasText(categoria)) {
            List<Produto> produtos = produtoRepository.findByEstabelecimentoAndCategoria(estabelecimento, categoria.trim());
            return produtoMapper.toDTOList(produtos);
        }
        return produtoMapper.toDTOList(produtoRepository.findByEstabelecimentoOrderByCategoriaAndOrdem(estabelecimento));
    }

    @Transactional
    public ProdutoResponseDTO atualizarOrdem(Long produtoId, Integer novaOrdem) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);

        produto.setOrdem(novaOrdem);
        return produtoMapper.toDTO(produtoRepository.save(produto));
    }

    private Integer proximaOrdem(Estabelecimento estabelecimento, String categoria) {
        Integer maiorOrdem = produtoRepository.findMaiorOrdemPorCategoria(estabelecimento, categoria);
        return maiorOrdem + 1;
    }

    private Usuario getUsuarioAutenticado() {
        Usuario usuarioAutenticado = SecurityUtils.getUsuarioAutenticado();
        if (usuarioAutenticado == null) {
            throw new BadCredentialsException("Usuário não autenticado");
        }
        return usuarioRepository.findByUsername(usuarioAutenticado.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private Produto getProdutoDoEstabelecimento(Estabelecimento estabelecimento, Long produtoId) {
        return produtoRepository.findByEstabelecimentoAndId(estabelecimento, produtoId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
    }
}
