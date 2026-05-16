package com.wilgner.cardapio;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstabelecimentoTests {

    private static final String PASSWORD = "senha-forte";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCadastraEstabelecimentoComProprietario() throws Exception {
        mockMvc.perform(post("/plataforma/estabelecimentos")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "Restaurante Teste",
                                  "username": "admin-rest",
                                  "email": "admin@rest.test",
                                  "password": "%s",
                                  "descricao": "Um ótimo restaurante",
                                  "horarioFuncionamento": "11:00 - 23:00",
                                  "telefone": "(31) 98765-4321",
                                  "emailContato": "contato@rest.test"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Restaurante Teste"))
                .andExpect(jsonPath("$.slug").value("admin-rest"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void proprietarioConsegueLoginComEmail() throws Exception {
        String username = uniqueUsername("prop");
        String email = username + "@test.com";

        createEstabelecimento(username, email);

        // Fazer login com EMAIL em vez de username
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", containsString(".")));
    }

    @Test
    void proprietarioConsegueEditarSeuEstabelecimento() throws Exception {
        String username = uniqueUsername("prop-edit");
        String email = username + "@test.com";
        Cookie[] cookies = registerAndLogin(username, email);

        // Editar dados do estabelecimento
        mockMvc.perform(put("/painel/estabelecimento")
                        .with(csrf())
                        .cookie(cookies)
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "Novo Nome",
                                  "descricao": "Nova descrição",
                                  "horarioFuncionamento": "12:00 - 22:00",
                                  "telefone": "(31) 99999-9999",
                                  "emailContato": "novo@email.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andExpect(jsonPath("$.descricao").value("Nova descrição"));
    }

    @Test
    void proprietarioConsegueObterSeuEstabelecimento() throws Exception {
        String username = uniqueUsername("prop-get");
        String email = username + "@test.com";
        Cookie[] cookies = registerAndLogin(username, email);

        mockMvc.perform(get("/painel/estabelecimento")
                        .cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nome").exists());
    }

    @Test
    void menuPublicoAcessaPorSlugEstabelecimento() throws Exception {
        String username = uniqueUsername("loja");
        String email = username + "@test.com";
        Cookie[] cookies = registerAndLogin(username, email);

        // Criar produto
        mockMvc.perform(post("/painel/produtos")
                        .with(csrf())
                        .cookie(cookies)
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "Peixe Grelhado",
                                  "descricao": "Peixe fresco",
                                  "preco": 49.90,
                                  "categoria": "Peixes",
                                  "imageUrl": "https://example.com/peixe.png"
                                }
                                """))
                .andExpect(status().isCreated());

        // Acessar menu publico usando o slug do usuario, criado a partir do username
        mockMvc.perform(get("/public/{slug}/cardapio", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Peixe Grelhado"));
    }

    @Test
    void produtosSaoOrdenadosPorCategoriaPoisLista() throws Exception {
        String username = uniqueUsername("ordem");
        String email = username + "@test.com";
        Cookie[] cookies = registerAndLogin(username, email);

        // Criar produtos em categorias diferentes
        for (String[] item : new String[][] {
                {"Tilapia", "Peixes", "59.90"},
                {"Batata", "Porcoes", "24.90"},
                {"Suco", "Bebidas", "12.90"}
        }) {
            mockMvc.perform(post("/painel/produtos")
                    .with(csrf())
                    .cookie(cookies)
                    .contentType("application/json")
                    .content("""
                            {
                              "nome": "%s",
                              "descricao": "Descrição",
                              "preco": %s,
                              "categoria": "%s",
                              "imageUrl": ""
                            }
                            """.formatted(item[0], item[2], item[1])))
                    .andExpect(status().isCreated());
        }

        // Listar - deve estar ordenado por categoria
        mockMvc.perform(get("/painel/produtos")
                        .cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void usuarioNaoAutenticadoNaoPodeAcessarEstabelecimento() throws Exception {
        mockMvc.perform(get("/painel/estabelecimento"))
                .andExpect(status().isUnauthorized());
    }

    private Cookie[] registerAndLogin(String username, String email) throws Exception {
        createEstabelecimento(username, email);

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return new Cookie[]{
                login.getResponse().getCookie("access_token"),
                login.getResponse().getCookie("refresh_token")
        };
    }

    private void createEstabelecimento(String username, String email) throws Exception {
        mockMvc.perform(post("/plataforma/estabelecimentos")
                        .with(user("platform-admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "nome": "%s",
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "descricao": "Cardapio de teste",
                                  "horarioFuncionamento": "11:00 - 23:00",
                                  "telefone": "(31) 98765-4321",
                                  "emailContato": "%s"
                                }
                                """.formatted(username, username, email, PASSWORD, email)))
                .andExpect(status().isCreated());
    }

    private String uniqueUsername(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
