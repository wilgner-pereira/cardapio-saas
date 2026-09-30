package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.product.ProdutoMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoRequestDTO;
import com.wilgner.cardapio.model.dto.product.ProdutoResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Produto;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.ProdutoRepository;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import com.wilgner.cardapio.security.SecurityAuditLogger;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminProdutoServiceImpl {
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoMapper produtoMapper;
    private final SecurityAuditLogger auditLogger;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final StorageImageLifecycle images;

    public AdminProdutoServiceImpl(ProdutoRepository produtoRepository, UsuarioRepository usuarioRepository,
                                   ProdutoMapper produtoMapper, SecurityAuditLogger auditLogger,
                                   EstabelecimentoRepository estabelecimentoRepository, StorageImageLifecycle images) {
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.produtoMapper = produtoMapper;
        this.auditLogger = auditLogger;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.images = images;
    }

    @Transactional
    public ProdutoResponseDTO criarProduto(ProdutoRequestDTO produtoRequestDTO) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();
        bloquearEstabelecimento(estabelecimento);

        images.replace(estabelecimento.getId(), null, produtoRequestDTO.imageUrl());

        Produto produto = new Produto();
        produto.setEstabelecimento(estabelecimento);
        produto.setUsuario(usuario);
        produto.setNome(produtoRequestDTO.nome().trim());
        produto.setDescricao(produtoRequestDTO.descricao().trim());
        produto.setPreco(produtoRequestDTO.preco());
        String categoria = produtoRequestDTO.categoria().trim();
        produto.setCategoria(categoria);
        produto.setCategoriaOrdem(ordemDaCategoriaOuProxima(estabelecimento, categoria));
        produto.setImagemUrl(produtoRequestDTO.imageUrl());
        produto.setOrdem(proximaOrdem(estabelecimento, categoria));

        Produto salvo = produtoRepository.save(produto);
        return produtoMapper.toDTO(salvo);
    }

    @Transactional
    public ProdutoResponseDTO atualizarProduto(ProdutoRequestDTO produtoRequestDTO, Long produtoId) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();
        bloquearEstabelecimento(estabelecimento);
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);
        images.replace(estabelecimento.getId(), produto.getImagemUrl(), produtoRequestDTO.imageUrl());
        String novaCategoria = produtoRequestDTO.categoria().trim();
        boolean mudouDeCategoria = !produto.getCategoria().equals(novaCategoria);
        Integer categoriaOrdem = ordemDaCategoriaOuProxima(estabelecimento, novaCategoria);
        Integer produtoOrdem = mudouDeCategoria ? proximaOrdem(estabelecimento, novaCategoria) : produto.getOrdem();

        produto.setNome(produtoRequestDTO.nome().trim());
        produto.setDescricao(produtoRequestDTO.descricao().trim());
        produto.setPreco(produtoRequestDTO.preco());
        produto.setCategoria(novaCategoria);
        produto.setCategoriaOrdem(categoriaOrdem);
        if (mudouDeCategoria) {
            produto.setOrdem(produtoOrdem);
        }
        produto.setImagemUrl(produtoRequestDTO.imageUrl());

        Produto salvo = produtoRepository.save(produto);
        return produtoMapper.toDTO(salvo);
    }

    @Transactional
    public void deleteProduto(Long produtoId) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();
        bloquearEstabelecimento(estabelecimento);
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);

        images.replace(estabelecimento.getId(), produto.getImagemUrl(), null);
        produtoRepository.delete(produto);
        auditLogger.recordCurrent(SecurityAuditLogger.Event.PRODUCT_DELETED,
                null, estabelecimento.getId(), produtoId);
    }

    @Transactional
    public ProdutoResponseDTO atualizarStatus(Long produtoId, boolean ativo) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();
        bloquearEstabelecimento(estabelecimento);
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);
        produto.setAtivo(ativo);
        return produtoMapper.toDTO(produtoRepository.save(produto));
    }

    @Transactional
    public ProdutoResponseDTO pesquisarProdutoPorId(Long produtoId) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);
        return produtoMapper.toDTO(produto);
    }

    @Transactional
    public List<ProdutoResponseDTO> listarProdutoDinamico(String categoria) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();

        if (StringUtils.hasText(categoria)) {
            List<Produto> produtos = produtoRepository.findByEstabelecimentoAndCategoria(estabelecimento, categoria.trim());
            return ordenarEMapear(produtos);
        }
        return produtoMapper.toDTOList(produtoRepository.findByEstabelecimentoOrderByCategoriaAndOrdem(estabelecimento));
    }

    @Transactional
    public ProdutoResponseDTO atualizarOrdem(Long produtoId, Integer novaOrdem) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();
        bloquearEstabelecimento(estabelecimento);
        Produto produto = getProdutoDoEstabelecimento(estabelecimento, produtoId);

        produto.setOrdem(novaOrdem);
        return produtoMapper.toDTO(produtoRepository.save(produto));
    }

    @Transactional
    public List<ProdutoResponseDTO> trocarOrdemCategorias(String categoria, String categoriaAlvo) {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();
        bloquearEstabelecimento(estabelecimento);
        List<Produto> produtos = produtoRepository.findByEstabelecimento(estabelecimento);

        encontrarOrdemCategoria(produtos, categoria);
        encontrarOrdemCategoria(produtos, categoriaAlvo);

        // Recover a dense, unique category sequence before swapping. Product order is independent.
        Map<String, Integer> ordens = produtos.stream().collect(Collectors.toMap(
                Produto::getCategoria, Produto::getCategoriaOrdem, Integer::min));
        List<String> categorias = new ArrayList<>(ordens.keySet());
        categorias.sort(Comparator.comparing((String nome) -> ordens.get(nome)).thenComparing(nome -> nome));
        java.util.Collections.swap(categorias, categorias.indexOf(categoria), categorias.indexOf(categoriaAlvo));
        for (int i = 0; i < categorias.size(); i++) ordens.put(categorias.get(i), i);

        if (categoria.equals(categoriaAlvo)) {
            return ordenarEMapear(produtos);
        }

        produtos.forEach(produto -> produto.setCategoriaOrdem(ordens.get(produto.getCategoria())));

        produtoRepository.saveAll(produtos);
        return ordenarEMapear(produtos);
    }

    private Integer proximaOrdem(Estabelecimento estabelecimento, String categoria) {
        Integer maiorOrdem = produtoRepository.findMaiorOrdemPorCategoria(estabelecimento, categoria);
        return maiorOrdem + 1;
    }

    private void bloquearEstabelecimento(Estabelecimento estabelecimento) {
        estabelecimentoRepository.findByIdForUpdate(estabelecimento.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));
    }

    private Integer ordemDaCategoriaOuProxima(Estabelecimento estabelecimento, String categoria) {
        Integer ordemExistente = produtoRepository.findOrdemDaCategoria(estabelecimento, categoria);
        return ordemExistente != null
                ? ordemExistente
                : produtoRepository.findMaiorOrdemDeCategoria(estabelecimento) + 1;
    }

    private Integer encontrarOrdemCategoria(List<Produto> produtos, String categoria) {
        return produtos.stream()
                .filter(produto -> produto.getCategoria().equals(categoria))
                .map(Produto::getCategoriaOrdem)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
    }

    private List<ProdutoResponseDTO> ordenarEMapear(List<Produto> produtos) {
        return produtoMapper.toDTOList(produtos.stream()
                .sorted(Comparator.comparing(Produto::getCategoriaOrdem)
                        .thenComparing(Produto::getCategoria)
                        .thenComparing(Produto::getOrdem)
                        .thenComparing(Produto::getId))
                .toList());
    }

    private Estabelecimento getEstabelecimentoAutenticado() {
        return getUsuarioAutenticado().getEstabelecimento();
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
