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

@Service
public class EstabelecimentoServiceImpl {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstabelecimentoMapper estabelecimentoMapper;

    public EstabelecimentoServiceImpl(EstabelecimentoRepository estabelecimentoRepository,
                                      UsuarioRepository usuarioRepository,
                                      EstabelecimentoMapper estabelecimentoMapper) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.estabelecimentoMapper = estabelecimentoMapper;
    }

    @Transactional
    public EstabelecimentoResponseDTO obterMeuEstabelecimento() {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();

        if (estabelecimento == null) {
            throw new ResourceNotFoundException("Estabelecimento não encontrado");
        }

        return estabelecimentoMapper.toDTO(estabelecimento);
    }

    @Transactional
    public EstabelecimentoResponseDTO atualizarMeuEstabelecimento(EstabelecimentoUpdateRequestDTO dto) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();

        if (estabelecimento == null) {
            throw new ResourceNotFoundException("Estabelecimento não encontrado");
        }

        estabelecimentoMapper.updateEntity(dto, estabelecimento);
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);

        return estabelecimentoMapper.toDTO(atualizado);
    }

    @Transactional
    public EstabelecimentoResponseDTO atualizarLogo(String logoUrl) {
        Usuario usuario = getUsuarioAutenticado();
        Estabelecimento estabelecimento = usuario.getEstabelecimento();

        if (estabelecimento == null) {
            throw new ResourceNotFoundException("Estabelecimento não encontrado");
        }

        estabelecimento.setLogoUrl(logoUrl);
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);

        return estabelecimentoMapper.toDTO(atualizado);
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
