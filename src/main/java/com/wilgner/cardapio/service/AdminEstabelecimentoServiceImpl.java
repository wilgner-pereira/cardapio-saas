package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ConflictException;
import com.wilgner.cardapio.exception.ResourceNotFoundException;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoCreateRequestDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoMapper;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResponseDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.EstabelecimentoResumoDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.PlatformClientDetailsDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.PlatformClientPasswordRequestDTO;
import com.wilgner.cardapio.model.dto.estabelecimento.PlatformClientUpdateRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterRequestDTO;
import com.wilgner.cardapio.model.dto.auth.RegisterResponseDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Role;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import com.wilgner.cardapio.security.SecurityAuditLogger;
import com.wilgner.cardapio.security.RefreshSessionService;
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
    private final SecurityAuditLogger auditLogger;
    private final RefreshSessionService refreshSessionService;

    public AdminEstabelecimentoServiceImpl(EstabelecimentoRepository estabelecimentoRepository,
                                           UsuarioRepository usuarioRepository,
                                           RoleRepository roleRepository,
                                           EstabelecimentoMapper estabelecimentoMapper,
                                           PasswordEncoder passwordEncoder,
                                           SecurityAuditLogger auditLogger,
                                           RefreshSessionService refreshSessionService) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.estabelecimentoMapper = estabelecimentoMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.refreshSessionService = refreshSessionService;
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

        Role roleUser = roleRepository.findByRoleName("ROLE_TENANT_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role padrão ROLE_TENANT_ADMIN ausente no banco"));
        usuario.setRoles(Set.of(roleUser));

        Usuario usuarioSalvo = usuarioRepository.save(usuario);
        auditLogger.recordCurrent(SecurityAuditLogger.Event.ESTABLISHMENT_CREATED,
                usuarioSalvo.getId(), estabelecimentoSalvo.getId(), null);

        return estabelecimentoMapper.toDTO(estabelecimentoSalvo);
    }

    @Transactional
    public RegisterResponseDTO cadastrarPrimeiroAdministrador(RegisterRequestDTO dto) {
        // Serialize first-admin setup across application instances sharing the DB.
        roleRepository.findByRoleNameForUpdate("ROLE_PLATFORM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ROLE_PLATFORM_ADMIN ausente no banco"));
        if (usuarioRepository.existsByRoleName("ROLE_PLATFORM_ADMIN")) {
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

        Role roleAdmin = roleRepository.findByRoleName("ROLE_PLATFORM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ROLE_PLATFORM_ADMIN ausente no banco"));
        usuario.setRoles(Set.of(roleAdmin));

        Usuario salvo = usuarioRepository.save(usuario);
        auditLogger.recordCurrent(SecurityAuditLogger.Event.PLATFORM_ADMIN_CREATED,
                salvo.getId(), estabelecimentoSalvo.getId(), null);
        return new RegisterResponseDTO(salvo.getUsername(), estabelecimentoSalvo.getSlug());
    }

    @Transactional
    public PlatformClientDetailsDTO obterEstabelecimento(Long id) {
        Estabelecimento estabelecimento = findClient(id);
        return clientDetails(estabelecimento, findTenantAdmin(id));
    }

    @Transactional
    public PlatformClientDetailsDTO atualizarEstabelecimento(Long id, PlatformClientUpdateRequestDTO dto) {
        Estabelecimento estabelecimento = findClient(id);
        Usuario usuario = findTenantAdmin(id);
        String username = dto.username().trim();
        String email = dto.email().trim().toLowerCase(java.util.Locale.ROOT);

        usuarioRepository.findByUsername(username)
                .filter(other -> !other.getId().equals(usuario.getId()))
                .ifPresent(other -> { throw new ConflictException("Username já está em uso"); });
        usuarioRepository.findByEmail(email)
                .filter(other -> !other.getId().equals(usuario.getId()))
                .ifPresent(other -> { throw new ConflictException("Email já está em uso"); });

        estabelecimento.setNome(dto.nome().trim());
        estabelecimento.setDescricao(dto.descricao());
        estabelecimento.setHorarioFuncionamento(dto.horarioFuncionamento());
        estabelecimento.setTelefone(dto.telefone());
        estabelecimento.setEndereco(dto.endereco());
        estabelecimento.setEmailContato(dto.emailContato());
        usuario.setUsername(username);
        usuario.setEmail(email);
        estabelecimentoRepository.saveAndFlush(estabelecimento);
        usuarioRepository.saveAndFlush(usuario);
        auditLogger.recordCurrent(SecurityAuditLogger.Event.ESTABLISHMENT_UPDATED,
                usuario.getId(), estabelecimento.getId(), null);
        return clientDetails(estabelecimento, usuario);
    }

    @Transactional
    public void redefinirSenha(Long id, PlatformClientPasswordRequestDTO dto) {
        findClient(id);
        Usuario usuario = findTenantAdmin(id);
        usuario.setPassword(passwordEncoder.encode(dto.password()));
        usuarioRepository.saveAndFlush(usuario);
        refreshSessionService.revokeAllForUser(usuario.getId());
        auditLogger.recordCurrent(SecurityAuditLogger.Event.CLIENT_PASSWORD_RESET,
                usuario.getId(), id, null);
    }

    @Transactional
    public List<EstabelecimentoResumoDTO> listarEstabelecimentos() {
        return estabelecimentoMapper.toResumoDTOList(estabelecimentoRepository.findPlatformClients());
    }

    @Transactional
    public EstabelecimentoResponseDTO ativarDesativarEstabelecimento(Long id, Boolean ativo) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findPlatformClientById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));

        estabelecimento.setAtivo(ativo);
        Estabelecimento atualizado = estabelecimentoRepository.save(estabelecimento);
        auditLogger.recordCurrent(Boolean.TRUE.equals(ativo)
                        ? SecurityAuditLogger.Event.ESTABLISHMENT_ACTIVATED
                        : SecurityAuditLogger.Event.ESTABLISHMENT_DISABLED,
                null, atualizado.getId(), null);

        return estabelecimentoMapper.toDTO(atualizado);
    }

    @Transactional
    public void deletarEstabelecimento(Long id) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findPlatformClientById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));

        estabelecimentoRepository.delete(estabelecimento);
        auditLogger.recordCurrent(SecurityAuditLogger.Event.ESTABLISHMENT_DELETED,
                null, id, null);
    }

    private Estabelecimento findClient(Long id) {
        return estabelecimentoRepository.findPlatformClientById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado"));
    }

    private Usuario findTenantAdmin(Long estabelecimentoId) {
        List<Usuario> admins = usuarioRepository.findTenantAdminsByEstabelecimentoId(estabelecimentoId);
        if (admins.isEmpty()) {
            throw new ResourceNotFoundException("Acesso do cliente não encontrado");
        }
        if (admins.size() != 1) {
            throw new ConflictException("Cliente possui mais de um administrador vinculado");
        }
        return admins.get(0);
    }

    private PlatformClientDetailsDTO clientDetails(Estabelecimento estabelecimento, Usuario usuario) {
        return new PlatformClientDetailsDTO(estabelecimentoMapper.toDTO(estabelecimento),
                usuario.getUsername(), usuario.getEmail());
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
