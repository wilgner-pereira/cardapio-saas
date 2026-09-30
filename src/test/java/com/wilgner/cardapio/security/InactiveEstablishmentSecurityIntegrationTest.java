package com.wilgner.cardapio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.RoleRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InactiveEstablishmentSecurityIntegrationTest {
    private static final String PASSWORD = "senha-forte";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EstabelecimentoRepository estabelecimentos;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void activeTenantCanLoginAndInactiveTenantCannot() throws Exception {
        String username = registerTenant("login-state");
        login(username).andExpect(status().isOk());

        setActive(username, false);
        login(username).andExpect(status().isUnauthorized());
    }

    @Test
    void existingAccessTokenStopsAuthorizingImmediatelyAndWorksAfterReactivation() throws Exception {
        String username = registerTenant("jwt-state");
        Cookie accessCookie = login(username).andExpect(status().isOk()).andReturn()
                .getResponse().getCookie("access_token");

        mockMvc.perform(get("/painel/estabelecimento").cookie(accessCookie))
                .andExpect(status().isOk());

        setActive(username, false);

        mockMvc.perform(get("/painel/estabelecimento").cookie(accessCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(accessCookie)
                        .param("tema", "brasa"))
                .andExpect(status().isUnauthorized());

        setActive(username, true);
        mockMvc.perform(get("/painel/estabelecimento").cookie(accessCookie))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdminRemainsEnabledWhenAdministrativeEstablishmentIsInactive() throws Exception {
        String username = unique("platform");
        Estabelecimento establishment = new Estabelecimento("Admin", username, "Admin account");
        establishment.setAtivo(false);
        establishment = estabelecimentos.save(establishment);

        Usuario platformAdmin = new Usuario(username, passwordEncoder.encode(PASSWORD),
                username + "@cardapio.test", establishment);
        platformAdmin.setRoles(Set.of(roles.findByRoleName("ROLE_PLATFORM_ADMIN").orElseThrow()));
        usuarios.save(platformAdmin);

        Cookie accessCookie = login(username).andExpect(status().isOk()).andReturn()
                .getResponse().getCookie("access_token");
        mockMvc.perform(get("/plataforma/estabelecimentos").cookie(accessCookie))
                .andExpect(status().isOk());
    }

    @Test
    void tenantCannotReadAnotherTenantsProduct() throws Exception {
        String firstTenant = registerTenant("tenant-one");
        String secondTenant = registerTenant("tenant-two");
        Cookie firstAccess = login(firstTenant).andReturn().getResponse().getCookie("access_token");
        Cookie secondAccess = login(secondTenant).andReturn().getResponse().getCookie("access_token");

        MvcResult created = mockMvc.perform(post("/painel/produtos")
                        .with(csrf())
                        .cookie(secondAccess)
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "Produto privado",
                                  "descricao": "Pertence ao segundo tenant",
                                  "preco": 12.50,
                                  "categoria": "Teste",
                                  "imageUrl": ""
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/painel/produtos/{id}", productId).cookie(firstAccess))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType("application/json")
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, PASSWORD)));
    }

    private String registerTenant(String prefix) throws Exception {
        String username = unique(prefix);
        mockMvc.perform(post("/plataforma/estabelecimentos")
                        .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "%s",
                                  "username": "%s",
                                  "password": "%s",
                                  "email": "%s@cardapio.test",
                                  "descricao": "Tenant de teste"
                                }
                                """.formatted(username, username, PASSWORD, username)))
                .andExpect(status().isCreated());
        return username;
    }

    private void setActive(String slug, boolean active) {
        Estabelecimento establishment = estabelecimentos.findBySlug(slug).orElseThrow();
        establishment.setAtivo(active);
        estabelecimentos.saveAndFlush(establishment);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
