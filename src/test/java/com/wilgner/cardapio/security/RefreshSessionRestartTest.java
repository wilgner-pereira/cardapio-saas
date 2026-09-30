package com.wilgner.cardapio.security;

import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefreshSessionRestartTest {
    private static final String DATABASE = "jdbc:h2:file:./target/refresh-restart-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    private static Cookie revoked;
    private static Cookie active;
    private static ApplicationContext previousContext;
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired UsuarioRepository users;
    @Autowired EstabelecimentoRepository establishments;
    @Autowired PasswordEncoder encoder;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
    }

    @Test
    @Order(1)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void revokeAndCloseEntireSpringContext() throws Exception {
        var e = new Estabelecimento();
        e.setNome("Restart test");
        e.setSlug("restart-test");
        e = establishments.save(e);
        String password = UUID.randomUUID().toString();
        users.save(new Usuario("restart-test", encoder.encode(password), "restart@example.test", e));
        String payload = "{\"username\":\"restart-test\",\"password\":\"" + password + "\"}";
        revoked = mvc.perform(post("/auth/login").contentType("application/json").content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
        active = mvc.perform(post("/auth/login").contentType("application/json").content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
        mvc.perform(post("/auth/logout").cookie(revoked).with(csrf())).andExpect(status().isOk());
        previousContext = context;
    }

    @Test
    @Order(2)
    void reopenedDatabaseKeepsRevocationAndOtherSession() throws Exception {
        assertThat(context).isNotSameAs(previousContext);
        assertThat(revoked).isNotNull();
        mvc.perform(post("/auth/refresh").cookie(revoked)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").cookie(active)).andExpect(status().isOk());
    }
}
