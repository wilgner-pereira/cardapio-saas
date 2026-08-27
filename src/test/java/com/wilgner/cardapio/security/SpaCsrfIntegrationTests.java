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

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SpaCsrfIntegrationTests {

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

        Role userRole = roleRepository.findByRoleName("ROLE_USER").orElseGet(() -> {
            Role role = new Role();
            role.setRoleName("ROLE_USER");
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
    void patchComValorBrutoDoCookieNoHeaderDevePermitirEdicoesSequenciais() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

        MvcResult firstPatchResult = patchWithCsrf(csrfCookie);

        Cookie rotatedCsrfCookie = Arrays.stream(firstPatchResult.getResponse().getCookies())
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .filter(cookie -> cookie.getMaxAge() != 0 && !cookie.getValue().isBlank())
                .reduce((first, last) -> last)
                .orElse(csrfCookie);

        patchWithCsrf(rotatedCsrfCookie);
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
}
