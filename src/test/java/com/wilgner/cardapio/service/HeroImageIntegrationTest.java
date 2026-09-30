package com.wilgner.cardapio.service;

import com.wilgner.cardapio.model.entity.*;
import com.wilgner.cardapio.repository.*;
import com.wilgner.cardapio.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HeroImageIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EstabelecimentoRepository establishments;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    Estabelecimento establishment;
    CustomUserDetails owner;

    @BeforeEach void setup() {
        String name = "hero-" + UUID.randomUUID();
        var entity = new Estabelecimento("Pizzaria", name, "Menu");
        entity.setLogoUrl("https://example.com/logo.png");
        establishment = establishments.save(entity);
        var account = new Usuario(name, "test-only", name + "@example.test", establishment);
        account.setRoles(Set.of(roles.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow()));
        owner = new CustomUserDetails(users.save(account));
    }

    String payload(String url) {
        return "{\"nome\":\"Pizzaria\",\"heroImageUrl\":\"" + url + "\"}";
    }

    @Test void savesReadsAndRemovesCoverWithoutChangingLogo() throws Exception {
        String url = "https://example.com/cover.jpg";
        mvc.perform(put("/painel/estabelecimento").with(user(owner)).with(csrf())
                .contentType("application/json").content(payload(url)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.heroImageUrl").value(url));
        assertThat(establishments.findById(establishment.getId()).orElseThrow().getHeroImageUrl()).isEqualTo(url);
        mvc.perform(get("/public/" + establishment.getSlug() + "/cardapio/info"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.heroImageUrl").value(url))
                .andExpect(jsonPath("$.id").doesNotExist());
        mvc.perform(put("/painel/estabelecimento").with(user(owner)).with(csrf())
                .contentType("application/json").content("{\"nome\":\"Novo nome\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.heroImageUrl").value(url));
        mvc.perform(put("/painel/estabelecimento").with(user(owner)).with(csrf())
                .contentType("application/json").content(payload("")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.heroImageUrl").isEmpty());
        var reloaded = establishments.findById(establishment.getId()).orElseThrow();
        assertThat(reloaded.getHeroImageUrl()).isNull();
        assertThat(reloaded.getLogoUrl()).isEqualTo("https://example.com/logo.png");
    }

    @Test void rejectsInvalidUrlsAndRequiresRoleAndCsrf() throws Exception {
        for (String invalid : new String[]{"javascript:alert(1)", "data:image/png;base64,abc", "blob:http://localhost/id", "not-a-url"}) {
            mvc.perform(put("/painel/estabelecimento").with(user(owner)).with(csrf())
                    .contentType("application/json").content(payload(invalid))).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/painel/estabelecimento").with(user(owner))
                .contentType("application/json").content(payload(""))).andExpect(status().isForbidden());
        mvc.perform(put("/painel/estabelecimento").with(user("reader").roles("USER")).with(csrf())
                .contentType("application/json").content(payload(""))).andExpect(status().isForbidden());
        assertThat(establishments.findById(establishment.getId()).orElseThrow().getHeroImageUrl()).isNull();
    }
}
