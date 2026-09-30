package com.wilgner.cardapio.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:refresh-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "api.security.cookie.secure=true", "api.security.cookie.same-site=None"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshSessionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository users;
    @Autowired EstabelecimentoRepository establishments;
    @Autowired RoleRepository roles;
    @Autowired RefreshSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Value("${api.security.token.issuer}") String issuer;
    @Value("${api.security.token.secret}") String secret;
    Usuario user;
    String password;

    @BeforeEach
    void setup() {
        String unique = UUID.randomUUID().toString();
        var establishment = new Estabelecimento();
        establishment.setNome("Refresh tests");
        establishment.setSlug("refresh-" + unique);
        establishment = establishments.save(establishment);
        password = UUID.randomUUID().toString();
        user = new Usuario("refresh-" + unique, encoder.encode(password), unique + "@example.test", establishment);
        user.setRoles(Set.of(roles.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow()));
        user = users.save(user);
    }

    @Test
    void loginRefreshRotatesAndRejectsReplayWithoutExposingTokens() throws Exception {
        var login = login();
        var original = responseCookie(login, "refresh_token");
        var next = refresh(original).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true)).andReturn();
        var previousJwt = JWT.decode(original.getValue());
        var nextJwt = JWT.decode(responseCookie(next, "refresh_token").getValue());
        assertThat(previousJwt.getId()).isNotBlank().isNotEqualTo(nextJwt.getId());
        assertThat(previousJwt.getClaim("sid").asString()).isEqualTo(nextJwt.getClaim("sid").asString());
        assertThat(previousJwt.getExpiresAt()).isEqualTo(nextJwt.getExpiresAt());
        var stored = sessions.findById(nextJwt.getClaim("sid").asString()).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(nextJwt.getId());
        assertThat(next.getResponse().getHeaders("Set-Cookie")).anyMatch(h -> h.contains("SameSite=None"));
        refresh(original).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Sessão expirada"));
        refresh(responseCookie(next, "refresh_token")).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesOnlyItsSessionAndRequiresCsrf() throws Exception {
        Cookie first = responseCookie(login(), "refresh_token");
        Cookie second = responseCookie(login(), "refresh_token");
        mvc.perform(post("/auth/logout").cookie(first)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").cookie(first).with(csrf())).andExpect(status().isOk())
                .andExpect(cookie().maxAge("access_token", 0)).andExpect(cookie().maxAge("refresh_token", 0));
        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").cookie(first).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void logoutWithPreRotationCookieRevokesSuccessor() throws Exception {
        Cookie previous = responseCookie(login(), "refresh_token");
        Cookie next = responseCookie(refresh(previous).andExpect(status().isOk()).andReturn(), "refresh_token");
        mvc.perform(post("/auth/logout").cookie(previous).with(csrf())).andExpect(status().isOk());
        refresh(next).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredJwtAndExpiredDatabaseSession() throws Exception {
        Cookie valid = responseCookie(login(), "refresh_token");
        var jwt = JWT.decode(valid.getValue());
        String expired = JWT.create().withIssuer(issuer).withSubject(user.getUsername())
                .withClaim("type", "refresh").withClaim("sid", jwt.getClaim("sid").asString()).withJWTId(jwt.getId())
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256(secret));
        refresh(new Cookie("refresh_token", expired)).andExpect(status().isUnauthorized());
        jdbc.update("update refresh_session set expires_at = ? where id = ?",
                Instant.now().minusSeconds(60), jwt.getClaim("sid").asString());
        refresh(valid).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongTokenTypesAndLegacyStatelessRefresh() throws Exception {
        var login = login();
        mvc.perform(get("/auth/validate").cookie(new Cookie("access_token", responseCookie(login, "refresh_token").getValue())))
                .andExpect(status().isUnauthorized());
        refresh(new Cookie("refresh_token", responseCookie(login, "access_token").getValue())).andExpect(status().isUnauthorized());
        String legacy = JWT.create().withIssuer(issuer).withSubject(user.getUsername()).withClaim("type", "refresh")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256(secret));
        refresh(new Cookie("refresh_token", legacy)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedOrInactiveAccountCannotRefresh() throws Exception {
        Cookie active = responseCookie(login(), "refresh_token");
        var establishment = user.getEstabelecimento();
        establishment.setAtivo(false);
        establishments.save(establishment);
        refresh(active).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").contentType("application/json").content(payload()))
                .andExpect(status().isUnauthorized());
        users.deleteById(user.getId());
        refresh(active).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshHasExactlyOneWinner() throws Exception {
        Cookie original = responseCookie(login(), "refresh_token");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
                return refresh(original).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/auth/login").contentType("application/json").content(payload()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
    }

    private String payload() {
        return "{\"username\":\"" + user.getUsername() + "\",\"password\":\"" + password + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions refresh(Cookie cookie) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(cookie));
    }

    private Cookie responseCookie(MvcResult result, String name) {
        return result.getResponse().getCookie(name);
    }
}
