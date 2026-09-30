package com.wilgner.cardapio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformDashboardIntegrationTest {
    private static final String PASSWORD = "test-only-password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository users;

    @Test
    void customerListExcludesPlatformAccountsAndCustomerEndpointsProtectThem() throws Exception {
        String platform = createAdmin();
        String tenant = unique("tenant");
        JsonNode client = createClient(tenant);
        long adminEstablishmentId = users.findByUsername(platform).orElseThrow().getEstabelecimento().getId();
        Cookie[] adminCookies = login(platform);

        String body = mvc.perform(get("/plataforma/estabelecimentos").cookie(adminCookies))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(client.get("slug").asText()).doesNotContain(platform);
        mvc.perform(get("/plataforma/estabelecimentos/{id}", adminEstablishmentId).cookie(adminCookies))
                .andExpect(status().isNotFound());
        for (String action : new String[]{"ativar", "desativar"}) {
            mvc.perform(patch("/plataforma/estabelecimentos/{id}/{action}", adminEstablishmentId, action)
                            .cookie(adminCookies).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(delete("/plataforma/estabelecimentos/{id}", adminEstablishmentId).cookie(adminCookies).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(users.findByUsername(platform)).isPresent();
        mvc.perform(get("/auth/session").cookie(adminCookies))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.session.roles[0]").value("ROLE_PLATFORM_ADMIN"));
    }

    @Test
    void platformCookieCanCreateAndReadAClientButCannotUseTheTenantPanel() throws Exception {
        Cookie[] adminCookies = login(createAdmin());
        String tenant = unique("new-client");
        String body = mvc.perform(post("/plataforma/estabelecimentos").cookie(adminCookies).with(csrf())
                        .contentType("application/json").content(clientPayload(tenant)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value(tenant))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        mvc.perform(get("/plataforma/estabelecimentos/{id}", id).cookie(adminCookies))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endereco").value("Rua de teste, 10"))
                .andExpect(jsonPath("$.tema").value("artesanal"))
                .andExpect(jsonPath("$.criadoEm").exists()).andExpect(jsonPath("$.atualizadoEm").exists())
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/painel/produtos").cookie(adminCookies)).andExpect(status().isForbidden());
        mvc.perform(get("/auth/session").cookie(adminCookies))
                .andExpect(jsonPath("$.session.roles.length()").value(1))
                .andExpect(jsonPath("$.session.roles[0]").value("ROLE_PLATFORM_ADMIN"));
    }

    @Test
    void disableAndReactivateThroughPlatformEndpointsControlsTenantAndPublicAccess() throws Exception {
        String tenant = unique("availability");
        long id = createClient(tenant).get("id").asLong();
        Cookie[] tenantCookies = login(tenant);
        Cookie[] adminCookies = login(createAdmin());
        mvc.perform(patch("/plataforma/estabelecimentos/{id}/desativar", id).cookie(adminCookies).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/painel/estabelecimento").cookie(tenantCookies)).andExpect(status().isUnauthorized());
        mvc.perform(get("/public/{slug}/cardapio", tenant)).andExpect(status().isNotFound());
        mvc.perform(post("/auth/refresh").cookie(tenantCookies)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/plataforma/estabelecimentos/{id}/ativar", id).cookie(adminCookies).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(get("/painel/estabelecimento").cookie(tenantCookies)).andExpect(status().isOk());
        mvc.perform(get("/public/{slug}/cardapio", tenant)).andExpect(status().isOk());
    }

    @Test
    void deletingAClientCascadesItsUsersProductsAndSessionsButPreservesStorageAccounting() throws Exception {
        String tenant = unique("delete-client");
        long id = createClient(tenant).get("id").asLong();
        Cookie[] tenantCookies = login(tenant);
        long userId = users.findByUsername(tenant).orElseThrow().getId();
        mvc.perform(post("/painel/produtos").cookie(tenantCookies).with(csrf()).contentType("application/json")
                        .content("""
                                {"nome":"Pizza","descricao":"Produto de teste","preco":20,"categoria":"Pizzas","imageUrl":""}
                                """))
                .andExpect(status().isCreated());
        String key = "test-" + UUID.randomUUID() + ".png";
        jdbc.update("INSERT INTO storage_object (estabelecimento_id,object_key,public_url,size_bytes,status) VALUES (?,?,?,?,?)",
                id, key, "https://example.test/" + key, 100, "STORED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_session WHERE usuario_id=?", Integer.class, userId)).isPositive();
        mvc.perform(delete("/plataforma/estabelecimentos/{id}", id).cookie(login(createAdmin())).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM estabelecimento WHERE id=?", Integer.class, id)).isZero();
        assertThat(users.findByUsername(tenant)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM produtos WHERE estabelecimento_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_session WHERE usuario_id=?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM storage_object WHERE object_key=? AND estabelecimento_id IS NULL", String.class, key)).isEqualTo("STORED");
        mvc.perform(get("/painel/estabelecimento").cookie(tenantCookies)).andExpect(status().isUnauthorized());
    }

    @Test
    void allPlatformOperationsRejectTenantsAndCookieMutationsRequireCsrf() throws Exception {
        String tenant = unique("permissions");
        long id = createClient(tenant).get("id").asLong();
        Cookie[] tenantCookies = login(tenant);
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/plataforma/estabelecimentos"), get("/plataforma/estabelecimentos/" + id),
                post("/plataforma/estabelecimentos"), post("/plataforma/admins"),
                patch("/plataforma/estabelecimentos/" + id + "/ativar"),
                patch("/plataforma/estabelecimentos/" + id + "/desativar"), delete("/plataforma/estabelecimentos/" + id)}) {
            mvc.perform(request.cookie(tenantCookies).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        Cookie[] adminCookies = login(createAdmin());
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/plataforma/estabelecimentos"), post("/plataforma/admins"),
                patch("/plataforma/estabelecimentos/" + id + "/ativar"),
                patch("/plataforma/estabelecimentos/" + id + "/desativar"), delete("/plataforma/estabelecimentos/" + id)}) {
            mvc.perform(request.cookie(adminCookies).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anExplicitDualRoleEstablishmentIsAlsoProtectedFromCustomerDeletion() throws Exception {
        String platform = createAdmin();
        long id = users.findByUsername(platform).orElseThrow().getEstabelecimento().getId();
        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE role_name='ROLE_TENANT_ADMIN'", Long.class);
        Long userId = users.findByUsername(platform).orElseThrow().getId();
        jdbc.update("INSERT INTO user_roles (user_id,role_id) VALUES (?,?)", userId, roleId);
        mvc.perform(delete("/plataforma/estabelecimentos/{id}", id).cookie(login(platform)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(users.findByUsername(platform)).isPresent();
    }

    private String createAdmin() throws Exception {
        String name = unique("platform");
        mvc.perform(post("/plataforma/admins").with(user("operator").roles("PLATFORM_ADMIN")).with(csrf())
                        .contentType("application/json").content("""
                                {"username":"%s","email":"%s@example.test","password":"%s"}
                                """.formatted(name, name, PASSWORD)))
                .andExpect(status().isCreated());
        return name;
    }

    private JsonNode createClient(String username) throws Exception {
        String body = mvc.perform(post("/plataforma/estabelecimentos")
                        .with(user("operator").roles("PLATFORM_ADMIN")).with(csrf())
                        .contentType("application/json").content(clientPayload(username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private Cookie[] login(String username) throws Exception {
        var response = mvc.perform(post("/auth/login").contentType("application/json")
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Cookie[]{response.getCookie("access_token"), response.getCookie("refresh_token")};
    }

    private String clientPayload(String username) {
        return """
                {"nome":"Restaurante de teste","username":"%s","email":"%s@example.test","password":"%s",
                 "descricao":"Cozinha artesanal","horarioFuncionamento":"18h às 23h","telefone":"(31) 99999-1111",
                 "endereco":"Rua de teste, 10","emailContato":"contato@example.test"}
                """.formatted(username, username, PASSWORD);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
