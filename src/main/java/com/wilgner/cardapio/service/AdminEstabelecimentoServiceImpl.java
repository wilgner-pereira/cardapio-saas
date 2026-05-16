package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ConflictException;
import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoCreateRequestDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoMapper;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResponseDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResumoDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Role;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class AdminEstabelecimentoServiceImpl {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final EstabelecimentoMapper estabelecimentoMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminEstabelecimentoServiceImpl(EstabelecimentoRepository estabelecimentoRepository,
                                           UsuarioRepository usuarioRepository,
                                           RoleRepository roleRepository,
                                           EstabelecimentoMapper estabelecimentoMapper,
                                           PasswordEncoder passwordEncoder) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.estabelecimentoMapper = estabelecimentoMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public EstabelecimentoResponseDTO cadastrarEstabelecimento(EstabelecimentoCreateRequestDTO dto) {
        String username = dto.username().trim();
        String email = dto.email().trim().toLowerCase();
        String slug = gerarSlug(username);

        if (estabelecimentoRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug já está em uso");
        }

        if (usuarioRepository.existsByUsernameOrEmail(username, email)) {
            throw new ConflictException("Username ou email já estão em uso");
        }

        Estabelecimento estabelecimento = estabelecimentoMapper.toEntity(dto);
        estabelecimento.setSlug(slug);
        Estabelecimento estabelecimentoSalvo = estabelecimentoRepository.save(estabelecimento);

        String senhaEncriptada = passwordEncoder.encode(dto.password());
        Usuario usuario = new Usuario(
                username,
                senhaEncriptada,
                email,
                estabelecimentoSalvo
        );

        Role roleUser = roleRepository.findByRoleName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Role padrão ROLE_USER ausente no banco"));
        usuario.setRoles(Set.of(roleUser));

        usuarioRepository.save(usuario);

        return estabelecimentoMapper.toDTO(estabelecimentoSalvo);
    }

    @Transactional
    public RegisterResponseDTO cadastrarPrimeiroAdministrador(RegisterRequestDTO dto) {
        if (usuarioRepository.existsByRoleName("ROLE_ADMIN")) {
            throw new ConflictException("Já existe um administrador cadastrado");
        }

        return cadastrarAdministrador(dto);
    }

    @Transactional
    public RegisterResponseDTO cadastrarAdministrador(RegisterRequestDTO dto) {
        String username = dto.username().trim();
        String email = dto.email().trim().toLowerCase();

        if (usuarioRepository.existsByUsernameOrEmail(username, email)) {
            throw new ConflictException("Username ou email já estão em uso");
        }

        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setNome("Administrador da Plataforma");
        estabelecimento.setSlug(gerarSlugUnico(username));
        estabelecimento.setDescricao("Conta administrativa da plataforma");
        estabelecimento.setAtivo(true);
        Estabelecimento estabelecimentoSalvo = estabelecimentoRepository.save(estabelecimento);

        Usuario usuario = new Usuario(
                username,
                passwordEncoder.encode(dto.password()),
                email,
                estabelecimentoSalvo
        );

        Role roleAdmin = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ROLE_ADMIN ausente no banco"));
        usuario.setRoles(Set.of(roleAdmin));

        Usuario salvo = usuarioRepository.save(usuario);
        return new RegisterResponseDTO(salvo.getUsername(), estabelecimentoSalvo.getSlug());
    }

    @Transactional
    public EstabelecimentoResponseDTO obterEstabelecimento(Long id) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));
        return estabelecimentoMapper.toDTO(estabelecimento);
    }

    @Transactional
    public List<EstabelecimentoResumoDTO> listarEstabelecimentos() {
        return estabelecimentoMapper.toResumoDTOList(estabelecimentoRepository.findAll());
    }

    @Transactional
    public EstabelecimentoResponseDTO ativarDesativarEstabelecimento(Long id, Boolean ativo) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));

        estabelecimento.setAtivo(ativo);
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);

        return estabelecimentoMapper.toDTO(atualizado);
    }

    @Transactional
    public void deletarEstabelecimento(Long id) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));

        estabelecimentoRepository.delete(estabelecimento);
    }

    private String gerarSlug(String username) {
        String slug = username.toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");

        return slug.isBlank() ? "cardapio" : slug;
    }

    private String gerarSlugUnico(String username) {
        String baseSlug = gerarSlug(username);
        String slug = baseSlug;
        int suffix = 2;

        while (estabelecimentoRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }

        return slug;
    }
}
