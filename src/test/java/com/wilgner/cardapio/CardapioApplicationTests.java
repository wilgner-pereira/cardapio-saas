
package com.wilgner.cardapio;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardapioApplicationTests {

    private static final String PASSWORD = "senha-forte";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/public/restaurante/cardapio").secure(true))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/painel/produtos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/plataforma/estabelecimentos")
                        .with(user("platform-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "loja com espaco",
                                  "password": "curta",
                                  "email": "email-invalido"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void loginReturnsAccessAndRefreshCookies() throws Exception {
        String username = uniqueUsername("admin");
        register(username);

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginPayload(username)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().value("access_token", containsString(".")))
                .andExpect(jsonPath("$.accessToken", containsString(".")))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void bearerTokenCanCreateProductWithoutCsrf() throws Exception {
        String username = uniqueUsername("swagger");
        String accessToken = registerAndLoginWithBearer(username);

        mockMvc.perform(post("/painel/produtos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "Hamburguer",
                                  "descricao": "Hamburguer artesanal",
                                  "preco": 29.90,
                                  "categoria": "Lanches",
                                  "imageUrl": "https://example.com/hamburguer.png"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Hamburguer"));
    }

    @Test
    void publicCardapioListsOnlyActiveProducts() throws Exception {
        String username = uniqueUsername("loja");
        Cookie[] cookies = registerAndLogin(username);

        MvcResult created = mockMvc.perform(post("/painel/produtos")
                        .with(csrf())
                        .cookie(cookies)
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "Pizza",
                                  "descricao": "Pizza grande",
                                  "preco": 39.90,
                                  "categoria": "Pizzas",
                                  "imageUrl": "https://example.com/pizza.png"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true))
                .andReturn();

        long produtoId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/public/{username}/cardapio", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Pizza"));

        mockMvc.perform(patch("/painel/produtos/{id}/status", produtoId)
                        .with(csrf())
                        .cookie(cookies)
                        .contentType("application/json")
                        .content("""
                                {
                                  "ativo": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(get("/public/{username}/cardapio", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void categoryOrderChangesWithoutChangingProductOrderInsideCategory() throws Exception {
        String username = uniqueUsername("categorias");
        Cookie[] cookies = registerAndLogin(username);

        createProduct(cookies, "Entrada A", "Entradas");
        createProduct(cookies, "Entrada B", "Entradas");
        createProduct(cookies, "Bebida A", "Bebidas");

        mockMvc.perform(patch("/painel/categorias/ordem")
                        .with(csrf())
                        .cookie(cookies)
                        .contentType("application/json")
                        .content("""
                                {
                                  "categoria": "Bebidas",
                                  "categoriaAlvo": "Entradas"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("Bebidas"))
                .andExpect(jsonPath("$[0].categoriaOrdem").value(0))
                .andExpect(jsonPath("$[0].ordem").value(0))
                .andExpect(jsonPath("$[1].categoria").value("Entradas"))
                .andExpect(jsonPath("$[1].nome").value("Entrada A"))
                .andExpect(jsonPath("$[1].ordem").value(0))
                .andExpect(jsonPath("$[2].nome").value("Entrada B"))
                .andExpect(jsonPath("$[2].ordem").value(1));

        mockMvc.perform(get("/painel/produtos").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("Bebidas"))
                .andExpect(jsonPath("$[1].nome").value("Entrada A"))
                .andExpect(jsonPath("$[2].nome").value("Entrada B"));

        mockMvc.perform(get("/public/{username}/cardapio", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("Bebidas"))
                .andExpect(jsonPath("$[1].nome").value("Entrada A"))
                .andExpect(jsonPath("$[2].nome").value("Entrada B"));
    }

    @Test
    void establishmentThemeDefaultsToArtesanalAndCanBeUpdatedToAllValidThemes() throws Exception {
        String username = uniqueUsername("tema");
        Cookie[] cookies = registerAndLogin(username);

        // Verifica que o tema padrão retornado no admin é 'artesanal'
        mockMvc.perform(get("/painel/estabelecimento").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("artesanal"));

        // Verifica que o tema padrão retornado no cardápio público é 'artesanal'
        mockMvc.perform(get("/public/{slug}/cardapio/info", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("artesanal"));

        // Atualiza para 'brasa'
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(cookies)
                        .param("tema", "brasa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("brasa"));

        // Verifica persistência no cardápio público
        mockMvc.perform(get("/public/{slug}/cardapio/info", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("brasa"));

        // Atualiza para 'atlantico'
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(cookies)
                        .param("tema", "atlantico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("atlantico"));

        // Atualiza para 'vinho'
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(cookies)
                        .param("tema", "vinho"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("vinho"));

        // Atualiza para 'grafite'
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(cookies)
                        .param("tema", "grafite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tema").value("grafite"));

        // Rejeita tema inválido com 400
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(cookies)
                        .param("tema", "invalido"))
                .andExpect(status().isBadRequest());

        // Rejeita tema vazio com 400
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .cookie(cookies)
                        .param("tema", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedUserCannotUpdateTheme() throws Exception {
        mockMvc.perform(put("/painel/estabelecimento/tema")
                        .with(csrf())
                        .param("tema", "brasa"))
                .andExpect(status().isUnauthorized());
    }

    private Cookie[] registerAndLogin(String username) throws Exception {
        register(username);
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginPayload(username)))
                .andExpect(status().isOk())
                .andReturn();

        return new Cookie[]{
                login.getResponse().getCookie("access_token"),
                login.getResponse().getCookie("refresh_token")
        };
    }

    private void createProduct(Cookie[] cookies, String nome, String categoria) throws Exception {
        mockMvc.perform(post("/painel/produtos")
                        .with(csrf())
                        .cookie(cookies)
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "%s",
                                  "descricao": "Produto para teste de ordenação",
                                  "preco": 10.00,
                                  "categoria": "%s",
                                  "imageUrl": ""
                                }
                                """.formatted(nome, categoria)))
                .andExpect(status().isCreated());
    }

    private String registerAndLoginWithBearer(String username) throws Exception {
        register(username);
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(loginPayload(username)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/plataforma/estabelecimentos")
                        .with(user("platform-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "%s",
                                  "username": "%s",
                                  "password": "%s",
                                  "email": "%s@cardapio.test",
                                  "descricao": "Cardapio de teste"
                                }
                                """.formatted(username, username, PASSWORD, username)))
                .andExpect(status().isCreated());
    }

    private String loginPayload(String username) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, PASSWORD);
    }

    private String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
