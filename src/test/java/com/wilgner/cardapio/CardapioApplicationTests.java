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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/admin/produto"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/auth/admin/register")
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

        mockMvc.perform(post("/auth/admin/login")
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

        mockMvc.perform(post("/admin/produto")
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

        MvcResult created = mockMvc.perform(post("/admin/produto")
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

        mockMvc.perform(patch("/admin/produto/{id}/status", produtoId)
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

    private Cookie[] registerAndLogin(String username) throws Exception {
        register(username);
        MvcResult login = mockMvc.perform(post("/auth/admin/login")
                        .contentType("application/json")
                        .content(loginPayload(username)))
                .andExpect(status().isOk())
                .andReturn();

        return new Cookie[]{
                login.getResponse().getCookie("access_token"),
                login.getResponse().getCookie("refresh_token")
        };
    }

    private String registerAndLoginWithBearer(String username) throws Exception {
        register(username);
        MvcResult login = mockMvc.perform(post("/auth/admin/login")
                        .contentType("application/json")
                        .content(loginPayload(username)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/auth/admin/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "email": "%s@cardapio.test"
                                }
                                """.formatted(username, PASSWORD, username)))
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
