package com.wilgner.cardapio.security;

import com.wilgner.cardapio.repository.UsuarioRepository;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.setup-admin.enabled=true",
        "app.setup-admin.token=test-only-setup-secret-with-at-least-32-characters",
        "api.security.cookie.secure=true",
        "api.security.cookie.same-site=None",
        "spring.datasource.url=jdbc:h2:mem:setup-security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupSecurityIntegrationTest {
    private static final String SECRET = "test-only-setup-secret-with-at-least-32-characters";
    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository users;
    @Autowired private EstabelecimentoRepository establishments;

    @BeforeEach
    void cleanIsolatedDatabase() {
        users.deleteAll();
        establishments.deleteAll();
    }

    @Test
    void missingOrWrongSecretCannotCreateAdminEvenWithCsrf() throws Exception {
        mvc.perform(post("/setup/admin").with(csrf()).contentType("application/json").content(payload("missing")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/setup/admin").with(csrf()).header("X-Setup-Token", "wrong")
                        .contentType("application/json").content(payload("wrong")))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isZero();
        assertThat(establishments.count()).isZero();
    }

    @Test
    void validSecretWithoutCsrfCannotBeRescuedByBearerHeader() throws Exception {
        mvc.perform(post("/setup/admin").header("X-Setup-Token", SECRET).header("Authorization", "Bearer invalid")
                        .contentType("application/json").content(payload("nocrsf")))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isZero();
    }

    @Test
    void validSetupCreatesPlatformAdminThenRejectsFurtherSetup() throws Exception {
        mvc.perform(post("/setup/admin").with(csrf()).header("X-Setup-Token", SECRET)
                        .contentType("application/json").content(payload("first-admin")))
                .andExpect(status().isCreated());
        assertThat(users.findWithRolesAndEstabelecimentoByUsername("first-admin").orElseThrow().getRoles())
                .extracting("roleName").containsExactly("ROLE_PLATFORM_ADMIN");
        mvc.perform(post("/setup/admin").with(csrf()).header("X-Setup-Token", SECRET)
                        .contentType("application/json").content(payload("second-admin")))
                .andExpect(status().isConflict());
        assertThat(users.count()).isEqualTo(1);
        assertThat(establishments.count()).isEqualTo(1);

        var login = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"username\":\"first-admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotHaveJsonPath())
                .andExpect(cookie().secure("access_token", true)).andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().httpOnly("access_token", true)).andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().attribute("access_token", "SameSite", "None"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PLATFORM_ADMIN")).andReturn();
        var refreshed = mvc.perform(post("/auth/refresh").cookie(login.getResponse().getCookie("refresh_token")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_PLATFORM_ADMIN"))
                .andExpect(cookie().secure("access_token", true)).andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().httpOnly("access_token", true)).andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn();
        mvc.perform(get("/plataforma/estabelecimentos").cookie(refreshed.getResponse().getCookie("access_token")))
                .andExpect(status().isOk());
        mvc.perform(get("/painel/produtos").cookie(refreshed.getResponse().getCookie("access_token")))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentSetupsCreateExactlyOneAdministrator() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return setup("parallel-a"); });
            var b = executor.submit(() -> { start.await(); return setup("parallel-b"); });
            start.countDown();
            assertThat(java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(establishments.count()).isEqualTo(1);
    }

    private int setup(String name) throws Exception {
        return mvc.perform(post("/setup/admin").with(csrf()).header("X-Setup-Token", SECRET)
                        .contentType("application/json").content(payload(name)))
                .andReturn().getResponse().getStatus();
    }

    private String payload(String name) {
        return "{\"username\":\"" + name + "\",\"email\":\"" + name + "@example.test\",\"password\":\"test-password\"}";
    }
}
