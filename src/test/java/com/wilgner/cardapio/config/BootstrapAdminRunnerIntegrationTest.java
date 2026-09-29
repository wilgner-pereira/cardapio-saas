package com.wilgner.cardapio.config;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "app.bootstrap-admin.enabled=true",
        "app.bootstrap-admin.username=bootstrap-startup-test",
        "app.bootstrap-admin.email=bootstrap-startup@example.test",
        "app.bootstrap-admin.password=test-bootstrap-password"
})
@ActiveProfiles("test")
@Import({BootstrapAdminRunner.class, BootstrapAdminRunnerIntegrationTest.PasswordConfiguration.class})
class BootstrapAdminRunnerIntegrationTest {
    @Autowired private BootstrapAdminRunner runner;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EstabelecimentoRepository estabelecimentos;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder encoder;
    private String username;

    @BeforeEach
    void configureRunner() {
        username = "bootstrap-" + UUID.randomUUID().toString().substring(0, 8);
        ReflectionTestUtils.setField(runner, "username", " " + username + " ");
        ReflectionTestUtils.setField(runner, "email", username + "@example.test");
        ReflectionTestUtils.setField(runner, "password", "test-bootstrap-password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_TENANT_ADMIN", "ROLE_PLATFORM_ADMIN"})
    void existingUserKeepsRolesCredentialsAndTenant(String roleName) {
        Usuario existing = existingUser(roleName);
        // Isolate username collisions from the separately tested email collision.
        ReflectionTestUtils.setField(runner, "email", username + "-new@example.test");
        long establishmentCount = estabelecimentos.count();
        long userCount = usuarios.count();

        assertThatIllegalStateException().isThrownBy(() -> runner.run())
                .withMessageContaining("Bootstrap admin abortado");

        Usuario unchanged = usuarios.findWithRolesAndEstabelecimentoByUsername(username).orElseThrow();
        assertThat(unchanged.getRoles()).extracting("roleName").containsExactly(roleName);
        assertThat(unchanged.getPassword()).isEqualTo("existing-test-hash");
        assertThat(unchanged.getEmail()).isEqualTo(existing.getEmail());
        assertThat(unchanged.getEstabelecimento().getId()).isEqualTo(existing.getEstabelecimento().getId());
        assertThat(estabelecimentos.count()).isEqualTo(establishmentCount);
        assertThat(usuarios.count()).isEqualTo(userCount);
    }

    @Test
    void emailCollisionDoesNotCreateAnotherTenant() {
        existingUser("ROLE_TENANT_ADMIN");
        long establishmentCount = estabelecimentos.count();
        ReflectionTestUtils.setField(runner, "username", username + "-new");
        ReflectionTestUtils.setField(runner, "email", " " + username.toUpperCase() + "@EXAMPLE.TEST ");

        assertThatIllegalStateException().isThrownBy(() -> runner.run());

        assertThat(usuarios.findByUsername(username + "-new")).isEmpty();
        assertThat(estabelecimentos.count()).isEqualTo(establishmentCount);
    }

    @Test
    void createsNewAdminWithUsableEncodedPassword() {
        runner.run();
        Usuario created = usuarios.findWithRolesAndEstabelecimentoByUsername(username).orElseThrow();
        assertThat(created.getRoles()).extracting("roleName").containsExactly("ROLE_PLATFORM_ADMIN");
        assertThat(encoder.matches("test-bootstrap-password", created.getPassword())).isTrue();
        assertThat(created.getEstabelecimento().getSlug()).isEqualTo(username);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseFailureRollsBackTheNewEstablishment() {
        long establishmentCount = estabelecimentos.count();
        // The database rejects an oversized email after the establishment has been inserted.
        ReflectionTestUtils.setField(runner, "email", "a".repeat(256) + "@example.test");

        assertThatThrownBy(() -> runner.run()).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(usuarios.findByUsername(username)).isEmpty();
        assertThat(estabelecimentos.count()).isEqualTo(establishmentCount);
    }

    private Usuario existingUser(String roleName) {
        Estabelecimento establishment = new Estabelecimento();
        establishment.setNome("Existing tenant");
        establishment.setSlug(username);
        establishment.setAtivo(true);
        Usuario user = new Usuario(username, "existing-test-hash", username + "@example.test",
                estabelecimentos.saveAndFlush(establishment));
        user.setRoles(Set.of(roles.findByRoleName(roleName).orElseThrow()));
        return usuarios.saveAndFlush(user);
    }

    @TestConfiguration
    static class PasswordConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
