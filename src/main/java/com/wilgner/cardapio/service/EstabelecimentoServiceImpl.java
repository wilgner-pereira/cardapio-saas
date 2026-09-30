package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoMapper;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResponseDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoUpdateRequestDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import com.wilgner.cardapio.model.entity.CardapioTema;

@Service
public class EstabelecimentoServiceImpl {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstabelecimentoMapper estabelecimentoMapper;
    private final StorageImageLifecycle images;
    private final jakarta.persistence.EntityManager entityManager;

    public EstabelecimentoServiceImpl(EstabelecimentoRepository estabelecimentoRepository,
                                      UsuarioRepository usuarioRepository,
                                      EstabelecimentoMapper estabelecimentoMapper, StorageImageLifecycle images,
                                      jakarta.persistence.EntityManager entityManager) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.estabelecimentoMapper = estabelecimentoMapper;
        this.images = images;
        this.entityManager = entityManager;
    }

    @Transactional
    public EstabelecimentoResponseDTO obterMeuEstabelecimento() {
        Estabelecimento estabelecimento = getEstabelecimentoAutenticado();

        return estabelecimentoMapper.toDTO(estabelecimento);
    }

    @Transactional
    public EstabelecimentoResponseDTO atualizarMeuEstabelecimento(EstabelecimentoUpdateRequestDTO dto) {
        Estabelecimento estabelecimento = getEstabelecimentoParaAtualizar();

        if (dto.heroImageUrl() != null) images.replace(estabelecimento.getId(), estabelecimento.getHeroImageUrl(), dto.heroImageUrl());
        estabelecimentoMapper.updateEntity(dto, estabelecimento);
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);

        return estabelecimentoMapper.toDTO(atualizado);
    }

    @Transactional
    public EstabelecimentoResponseDTO atualizarLogo(String logoUrl) {
        Estabelecimento estabelecimento = getEstabelecimentoParaAtualizar();

        images.replace(estabelecimento.getId(), estabelecimento.getLogoUrl(), logoUrl);
        estabelecimento.setLogoUrl(logoUrl);
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);

        return estabelecimentoMapper.toDTO(atualizado);
    }

    @Transactional
    public EstabelecimentoResponseDTO atualizarTema(String tema) {
        CardapioTema temaValidado = CardapioTema.fromValor(tema);
        Estabelecimento estabelecimento = getEstabelecimentoParaAtualizar();

        estabelecimento.setTema(temaValidado.getValor());
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);

        return estabelecimentoMapper.toDTO(atualizado);
    }

    private Estabelecimento getEstabelecimentoParaAtualizar() {
        Long id = getEstabelecimentoAutenticado().getId();
        Estabelecimento locked = estabelecimentoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));
        entityManager.refresh(locked);
        return locked;
    }

    private Estabelecimento getEstabelecimentoAutenticado() {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();

        if (estabelecimento == null) {
            throw new ResourceNotFoundException("Estabelecimento não encontrado");
        }
        return estabelecimento;
    }

    private Usuario getUsuarioAutenticado() {
        Usuario usuarioAutenticado = SecurityUtils.getUsuarioAutenticado();
        if (usuarioAutenticado == null) {
            throw new BadCredentialsException("Usuário não autenticado");
        }
        return usuarioRepository.findByUsername(usuarioAutenticado.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}
