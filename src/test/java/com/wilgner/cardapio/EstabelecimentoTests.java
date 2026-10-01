package com.wilgner.cardapio;

import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private ObjectMapper json;

    @Autowired
    private EstabelecimentoRepository estabelecimentos;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
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
                .andExpect(jsonPath("$.accessToken").doesNotHaveJsonPath())
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().value("access_token", containsString(".")));
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

    @ParameterizedTest
    @ValueSource(strings = {"https://instagram.com/pizzaria", "http://instagram.com/pizzaria"})
    void redesSociaisSaoPersistidasLidasEPodemSerRemovidas(String instagramUrl) throws Exception {
        String slug = uniqueUsername("social");
        Cookie[] cookies = registerAndLogin(slug, slug + "@test.com");
        String whatsapp = "+55 (34) 99999-9999";
        mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nome", "Pizzaria", "instagramUrl", instagramUrl, "whatsapp", whatsapp))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value(instagramUrl))
                .andExpect(jsonPath("$.whatsapp").value(whatsapp));

        var saved = estabelecimentos.findBySlug(slug).orElseThrow();
        assertThat(saved.getInstagramUrl()).isEqualTo(instagramUrl);
        assertThat(saved.getWhatsapp()).isEqualTo(whatsapp);
        mockMvc.perform(get("/painel/estabelecimento").cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value(instagramUrl))
                .andExpect(jsonPath("$.whatsapp").value(whatsapp));
        mockMvc.perform(get("/public/{slug}/cardapio/info", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").value(instagramUrl))
                .andExpect(jsonPath("$.whatsapp").value(whatsapp))
                .andExpect(jsonPath("$.id").doesNotExist());

        // Older clients can omit the fields or send null without erasing saved links.
        for (String payload : new String[]{"{\"nome\":\"Pizzaria\"}",
                "{\"nome\":\"Pizzaria\",\"instagramUrl\":null,\"whatsapp\":null}"}) {
            mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                            .contentType("application/json").content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.instagramUrl").value(instagramUrl))
                    .andExpect(jsonPath("$.whatsapp").value(whatsapp));
        }
        mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                        .contentType("application/json")
                        .content("{\"nome\":\"Pizzaria\",\"instagramUrl\":\"\",\"whatsapp\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").isEmpty())
                .andExpect(jsonPath("$.whatsapp").isEmpty());
        var cleared = estabelecimentos.findBySlug(slug).orElseThrow();
        assertThat(cleared.getInstagramUrl()).isNull();
        assertThat(cleared.getWhatsapp()).isNull();
        mockMvc.perform(get("/public/{slug}/cardapio/info", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").isEmpty())
                .andExpect(jsonPath("$.whatsapp").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,test", "ftp://instagram.com/test", "not-a-url",
            "https://", "https:///perfil", "https://instagram.com/with space", "https://instagram.com:invalid/test",
            "https://instagram.com@evil.example/test", "https://instagram.com\\evil.example/test"})
    void instagramInvalidoNaoPodeSerSalvo(String invalid) throws Exception {
        String slug = uniqueUsername("bad-social");
        Cookie[] cookies = registerAndLogin(slug, slug + "@test.com");
        mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nome", "Pizzaria", "instagramUrl", invalid))))
                .andExpect(status().isBadRequest());
        assertThat(estabelecimentos.findBySlug(slug).orElseThrow().getInstagramUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>1</script>", "abc123", "https://wa.me/123", "+( )-", "123\n456", "123\t456"})
    void whatsappInvalidoNaoPodeSerSalvo(String invalid) throws Exception {
        String slug = uniqueUsername("bad-wa");
        Cookie[] cookies = registerAndLogin(slug, slug + "@test.com");
        mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nome", "Pizzaria", "whatsapp", invalid))))
                .andExpect(status().isBadRequest());
        assertThat(estabelecimentos.findBySlug(slug).orElseThrow().getWhatsapp()).isNull();
    }

    @Test
    void limitesDasRedesSociaisSaoValidados() throws Exception {
        String slug = uniqueUsername("social-size");
        Cookie[] cookies = registerAndLogin(slug, slug + "@test.com");
        String instagram = "https://instagram.com/" + "a".repeat(2048 - "https://instagram.com/".length());
        String whatsapp = "1".repeat(30);
        mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                        .contentType("application/json").content(json.writeValueAsString(Map.of(
                                "nome", "Pizzaria", "instagramUrl", instagram, "whatsapp", whatsapp))))
                .andExpect(status().isOk());
        for (var fields : java.util.List.of(Map.of("instagramUrl", instagram + "a"), Map.of("whatsapp", whatsapp + "1"))) {
            var payload = new java.util.HashMap<>(fields);
            payload.put("nome", "Pizzaria");
            mockMvc.perform(put("/painel/estabelecimento").with(csrf()).cookie(cookies)
                            .contentType("application/json").content(json.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void edicaoDasRedesSociaisMantemTenantRoleECsrf() throws Exception {
        String slug = uniqueUsername("owner-social");
        Cookie[] cookies = registerAndLogin(slug, slug + "@test.com");
        String otherSlug = uniqueUsername("other-social");
        Cookie[] otherCookies = registerAndLogin(otherSlug, otherSlug + "@test.com");
        Long otherId = estabelecimentos.findBySlug(otherSlug).orElseThrow().getId();
        String payload = json.writeValueAsString(Map.of("nome", "Pizzaria", "instagramUrl", "https://instagram.com/owner",
                "whatsapp", "+1 (202) 555-0123", "id", otherId));
        mockMvc.perform(put("/painel/estabelecimento").cookie(cookies).contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/painel/estabelecimento").with(user("platform-admin").roles("PLATFORM_ADMIN")).with(csrf())
                        .contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/painel/estabelecimento").cookie(cookies).with(csrf())
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug));
        mockMvc.perform(get("/painel/estabelecimento").cookie(otherCookies))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").isEmpty())
                .andExpect(jsonPath("$.whatsapp").isEmpty());
        mockMvc.perform(get("/public/{slug}/cardapio/info", otherSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagramUrl").isEmpty())
                .andExpect(jsonPath("$.whatsapp").isEmpty());
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
                        .with(user("platform-admin").roles("PLATFORM_ADMIN"))
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
