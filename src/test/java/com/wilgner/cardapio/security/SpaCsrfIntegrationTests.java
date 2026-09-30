package com.wilgner.cardapio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoOrdemRequestDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Role;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SpaCsrfIntegrationTests {

    @Test
    void sessionBootstrapReturnsOnlyStateForAnonymousAndKeepsProtectedRoutesSecured() throws Exception {
        mockMvc.perform(get("/auth/session"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.refreshAvailable").value(false)).andExpect(jsonPath("$.session").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/auth/session").cookie(new Cookie("refresh_token", "present")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshAvailable").value(true))
                .andExpect(jsonPath("$.session").isEmpty());
        mockMvc.perform(get("/auth/session").cookie(accessTokenCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.session.userId").isNumber()).andExpect(jsonPath("$.session.accessToken").doesNotExist());
        mockMvc.perform(get("/auth/validate")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/painel/produtos")).andExpect(status().isUnauthorized());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie accessTokenCookie;

    @BeforeEach
    void setup() {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setNome("SPA CSRF " + UUID.randomUUID());
        estabelecimento.setSlug("spa-csrf-" + UUID.randomUUID());
        estabelecimento.setAtivo(true);
        estabelecimentoRepository.save(estabelecimento);

        Role userRole = roleRepository.findByRoleName("ROLE_TENANT_ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setRoleName("ROLE_TENANT_ADMIN");
            return roleRepository.save(role);
        });

        Usuario user = new Usuario();
        user.setUsername("spa-csrf-" + UUID.randomUUID());
        user.setPassword("password");
        user.setEmail("spa-csrf-" + UUID.randomUUID() + "@test.com");
        user.setEstabelecimento(estabelecimento);
        user.setRoles(Set.of(userRole));
        usuarioRepository.save(user);

        accessTokenCookie = new Cookie("access_token", tokenService.generateAccessToken(user));
    }

    @Test
    void tokenDoCookiePermaneceValidoAposGetsEEdicoesSequenciaisEParalelas() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult validate = mockMvc.perform(get("/auth/validate").cookie(accessTokenCookie, csrfCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertCsrfCookieWasNotRotated(validate, csrfCookie);
        MvcResult products = mockMvc.perform(get("/painel/produtos").cookie(accessTokenCookie, csrfCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertCsrfCookieWasNotRotated(products, csrfCookie);

        patchWithCsrf(csrfCookie);
        patchWithCsrf(csrfCookie);

        List<CompletableFuture<MvcResult>> parallel = List.of(
                CompletableFuture.supplyAsync(() -> patchUnchecked(csrfCookie)),
                CompletableFuture.supplyAsync(() -> patchUnchecked(csrfCookie))
        );
        CompletableFuture.allOf(parallel.toArray(CompletableFuture[]::new)).join();
        for (CompletableFuture<MvcResult> call : parallel) {
            org.assertj.core.api.Assertions.assertThat(call.join().getResponse().getStatus()).isEqualTo(404);
            assertCsrfCookieWasNotRotated(call.join(), csrfCookie);
        }
    }

    private MvcResult patchWithCsrf(Cookie csrfCookie) throws Exception {
        return mockMvc.perform(patch("/painel/produtos/1/ordem")
                        .cookie(accessTokenCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProdutoOrdemRequestDTO(0))))
                .andExpect(status().isNotFound())
                .andReturn();
    }

    private void assertCsrfCookieWasNotRotated(MvcResult result, Cookie expected) {
        Cookie responseCookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (responseCookie != null) {
            org.assertj.core.api.Assertions.assertThat(responseCookie.getValue()).isEqualTo(expected.getValue());
            org.assertj.core.api.Assertions.assertThat(responseCookie.getMaxAge()).isNotZero();
        }
    }

    private MvcResult patchUnchecked(Cookie csrfCookie) {
        try {
            return patchWithCsrf(csrfCookie);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void logoutRejectsMissingOrInvalidCsrfEvenWithBearerHeader() throws Exception {
        for (String authorization : new String[]{"", "Bearer invalid", "Bearer " + accessTokenCookie.getValue()}) {
            mockMvc.perform(post("/auth/logout").cookie(accessTokenCookie).header("Authorization", authorization))
                    .andExpect(status().isForbidden()).andExpect(cookie().doesNotExist("access_token"));
        }
        mockMvc.perform(post("/auth/logout").cookie(accessTokenCookie, new Cookie("XSRF-TOKEN", "expected"))
                        .header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden()).andExpect(cookie().doesNotExist("access_token"));
        mockMvc.perform(get("/auth/validate").cookie(accessTokenCookie)).andExpect(status().isOk());
        mockMvc.perform(post(java.net.URI.create("/auth/log%6fut")).cookie(accessTokenCookie)
                        .header("Authorization", "Bearer " + accessTokenCookie.getValue()))
                .andExpect(status().isForbidden()).andExpect(cookie().doesNotExist("access_token"));
    }

    @Test
    void csrfJsonWorksForCrossOriginSpaLogoutAndPreservesCookieAttributes() throws Exception {
        var result = mockMvc.perform(get("/auth/csrf").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(cookie().secure("XSRF-TOKEN", false))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        String tokenFromJson = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        org.assertj.core.api.Assertions.assertThat(tokenFromJson).isNotEqualTo(csrfCookie.getValue());
        var anotherCsrf = mockMvc.perform(get("/auth/csrf").cookie(csrfCookie))
                .andExpect(status().isOk()).andReturn();
        String anotherMaskedToken = objectMapper.readTree(anotherCsrf.getResponse().getContentAsString()).get("token").asText();
        org.assertj.core.api.Assertions.assertThat(anotherMaskedToken).isNotEqualTo(tokenFromJson);
        // Neither a raw cookie token on the masked header nor a masked token on
        // the raw header may accidentally satisfy the CSRF comparison.
        mockMvc.perform(post("/auth/logout").cookie(accessTokenCookie, csrfCookie)
                        .header("X-CSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/logout").cookie(accessTokenCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", tokenFromJson))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/logout").header("Origin", "http://localhost:5173")
                        .cookie(accessTokenCookie, csrfCookie).header("X-CSRF-TOKEN", tokenFromJson))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));
        mockMvc.perform(get("/auth/csrf").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
    }
}
