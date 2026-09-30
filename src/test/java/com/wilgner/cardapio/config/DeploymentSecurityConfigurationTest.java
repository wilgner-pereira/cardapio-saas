package com.wilgner.cardapio.config;

import com.wilgner.cardapio.controller.SetupController;
import com.wilgner.cardapio.security.SecurityAuditLogger;
import com.wilgner.cardapio.service.AdminEstabelecimentoServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DeploymentSecurityConfigurationTest {
    @Test
    void setupDisabledHasNoControllerAndEnablingWithoutStrongSecretFails() {
        var context = new ApplicationContextRunner().withUserConfiguration(SetupController.class)
                .withBean(AdminEstabelecimentoServiceImpl.class, () -> mock(AdminEstabelecimentoServiceImpl.class))
                .withBean(SecurityAuditLogger.class, () -> mock(SecurityAuditLogger.class));
        context.run(c -> assertThat(c).doesNotHaveBean(SetupController.class));
        context.withPropertyValues("app.setup-admin.enabled=true").run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("app.setup-admin.enabled=true", "app.setup-admin.token=short")
                .run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("app.setup-admin.enabled=true", "app.setup-admin.token=test-only-token-with-at-least-32-characters")
                .run(c -> assertThat(c).hasSingleBean(SetupController.class));
    }

    @Test
    void supabaseRequiresExplicitConnectionSettings() throws IOException {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:application-supabase.properties"));
        for (String key : new String[]{"spring.datasource.url", "spring.datasource.username", "supabase.url"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> env.getRequiredProperty(key));
        }
        env.setProperty("DATABASE_URL", "jdbc:postgresql://example.invalid/database");
        env.setProperty("DATABASE_USERNAME", "example-user");
        env.setProperty("SUPABASE_URL", "https://example.invalid");
        assertThat(env.getRequiredProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://example.invalid/database");
        assertThat(env.getRequiredProperty("spring.datasource.username")).isEqualTo("example-user");
        assertThat(env.getRequiredProperty("supabase.url")).isEqualTo("https://example.invalid");
    }

    @Test
    void productionProfilesKeepSetupDisabledByDefault() throws IOException {
        for (String profile : new String[]{"prod", "supabase"}) {
            MockEnvironment env = new MockEnvironment();
            env.getPropertySources().addLast(new ResourcePropertySource("classpath:application-" + profile + ".properties"));
            assertThat(env.getProperty("app.setup-admin.enabled", Boolean.class)).isFalse();
        }
    }
}
