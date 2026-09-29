package com.wilgner.cardapio.config;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Role;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {
    @Mock private UsuarioRepository usuarios;
    @Mock private EstabelecimentoRepository estabelecimentos;
    @Mock private RoleRepository roles;
    @Mock private PasswordEncoder encoder;
    private BootstrapAdminRunner runner;
    private Role admin;

    @BeforeEach
    void setup() {
        runner = new BootstrapAdminRunner(usuarios, estabelecimentos, roles, encoder);
        ReflectionTestUtils.setField(runner, "username", " bootstrap ");
        ReflectionTestUtils.setField(runner, "email", " BOOTSTRAP@EXAMPLE.TEST ");
        ReflectionTestUtils.setField(runner, "password", "test-bootstrap-password");
        admin = new Role();
        admin.setRoleName("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void existingIdentityIsRejectedBeforeAnyWrite() {
        when(roles.findByRoleName("ROLE_PLATFORM_ADMIN")).thenReturn(Optional.of(admin));
        when(usuarios.existsByUsernameOrEmail("bootstrap", "bootstrap@example.test")).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(() -> runner.run())
                .withMessageContaining("Bootstrap admin abortado");

        verify(usuarios, never()).save(any());
        verifyNoInteractions(estabelecimentos, encoder);
    }

    @Test
    void newIdentityGetsOnlyAdminRoleAndEncodedPassword() {
        when(roles.findByRoleName("ROLE_PLATFORM_ADMIN")).thenReturn(Optional.of(admin));
        when(encoder.encode("test-bootstrap-password")).thenReturn("encoded-test-password");
        when(estabelecimentos.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        runner.run();

        ArgumentCaptor<Usuario> saved = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(saved.capture());
        Usuario user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("bootstrap");
        assertThat(user.getEmail()).isEqualTo("bootstrap@example.test");
        assertThat(user.getPassword()).isEqualTo("encoded-test-password");
        assertThat(user.getRoles()).containsExactly(admin);
        assertThat(user.getEstabelecimento()).extracting(Estabelecimento::getSlug).isEqualTo("bootstrap");
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "email", "password"})
    void missingConfigurationFailsWithoutDatabaseWrites(String field) {
        ReflectionTestUtils.setField(runner, field, " ");
        assertThatIllegalStateException().isThrownBy(() -> runner.run());
        verifyNoInteractions(usuarios, estabelecimentos, roles, encoder);
    }

    @Test
    void missingAdminRoleFailsWithoutCreatingAccount() {
        assertThatIllegalStateException().isThrownBy(() -> runner.run())
                .withMessageContaining("ROLE_PLATFORM_ADMIN ausente");
        verifyNoInteractions(usuarios, estabelecimentos, encoder);
    }
}
