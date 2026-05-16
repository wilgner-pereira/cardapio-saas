package com.wilgner.cardapio.config;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Role;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminRunner implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.username:}")
    private String username;

    @Value("${app.bootstrap-admin.email:}")
    private String email;

    @Value("${app.bootstrap-admin.password:}")
    private String password;

    public BootstrapAdminRunner(UsuarioRepository usuarioRepository,
                                EstabelecimentoRepository estabelecimentoRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("Bootstrap admin habilitado, mas username, email ou password não foram configurados");
        }

        Role roleAdmin = roleRepository.findByRoleName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Role ROLE_ADMIN ausente no banco"));

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase();

        Usuario usuario = usuarioRepository.findWithRolesAndEstabelecimentoByUsername(normalizedUsername)
                .orElseGet(() -> criarUsuarioAdmin(normalizedUsername, normalizedEmail));

        var roles = new HashSet<>(usuario.getRoles());
        roles.add(roleAdmin);
        usuario.setRoles(roles);

        usuarioRepository.save(usuario);
    }

    private Usuario criarUsuarioAdmin(String normalizedUsername, String normalizedEmail) {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setNome("Administrador");
        estabelecimento.setSlug(gerarSlug(normalizedUsername));
        estabelecimento.setDescricao("Estabelecimento administrativo da plataforma");
        estabelecimento.setAtivo(true);
        Estabelecimento estabelecimentoSalvo = estabelecimentoRepository.save(estabelecimento);

        return new Usuario(
                normalizedUsername,
                passwordEncoder.encode(password),
                normalizedEmail,
                estabelecimentoSalvo
        );
    }

    private String gerarSlug(String value) {
        String baseSlug = value.toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (baseSlug.isBlank()) {
            baseSlug = "admin";
        }

        String slug = baseSlug;
        int suffix = 2;
        while (estabelecimentoRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }

        return slug;
    }
}
