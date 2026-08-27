package com.wilgner.cardapio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.model.dto.product.ProdutoStatusRequestDTO;
import com.wilgner.cardapio.model.entity.Estabelecimento;
import com.wilgner.cardapio.model.entity.Role;
import com.wilgner.cardapio.model.entity.Usuario;
import com.wilgner.cardapio.repository.EstabelecimentoRepository;
import com.wilgner.cardapio.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.wilgner.cardapio.repository.RoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityIntegrationTests {

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

    private Cookie adminCookie;
    private Cookie userCookie;
    
    @BeforeEach
    void setup() {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setNome("Test Estabelecimento " + UUID.randomUUID());
        estabelecimento.setSlug("test-est-" + UUID.randomUUID());
        estabelecimento.setAtivo(true);
        estabelecimentoRepository.save(estabelecimento);

        Role userRole = roleRepository.findByRoleName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setRoleName("ROLE_USER");
            return roleRepository.save(r);
        });

        Role adminRole = roleRepository.findByRoleName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setRoleName("ROLE_ADMIN");
            return roleRepository.save(r);
        });

        Usuario user = new Usuario();
        user.setUsername("user-" + UUID.randomUUID());
        user.setPassword("password");
        user.setEmail("user-" + UUID.randomUUID() + "@test.com");
        user.setEstabelecimento(estabelecimento);
        user.setRoles(java.util.Set.of(userRole));
        usuarioRepository.save(user);

        Usuario admin = new Usuario();
        admin.setUsername("admin-" + UUID.randomUUID());
        admin.setPassword("password");
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.com");
        admin.setEstabelecimento(estabelecimento);
        admin.setRoles(java.util.Set.of(adminRole));
        usuarioRepository.save(admin);

        String userToken = tokenService.generateAccessToken(user);
        String adminToken = tokenService.generateAccessToken(admin);

        userCookie = new Cookie("access_token", userToken);
        adminCookie = new Cookie("access_token", adminToken);
    }

    @Test
    void acessoSemToken_deveRetornar401() throws Exception {
        mockMvc.perform(get("/painel/estabelecimento"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acessoComTokenValido_deveRetornar200() throws Exception {
        mockMvc.perform(get("/painel/estabelecimento").cookie(userCookie))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioSemPermissao_deveRetornar403() throws Exception {
        // ADMIN não tem permissão para /painel/estabelecimento que é apenas USER
        mockMvc.perform(get("/painel/estabelecimento").cookie(adminCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchAutenticadoSemCsrf_deveRetornar403() throws Exception {
        ProdutoStatusRequestDTO dto = new ProdutoStatusRequestDTO(false);
        mockMvc.perform(patch("/painel/produtos/1/status")
                        .cookie(userCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchAutenticadoComCsrf_deveRetornar404Ou200() throws Exception {
        ProdutoStatusRequestDTO dto = new ProdutoStatusRequestDTO(false);
        mockMvc.perform(patch("/painel/produtos/1/status")
                        .cookie(userCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound()); 
    }

    @Test
    void deleteAutenticadoComCsrf_deveRetornar404Ou204() throws Exception {
        mockMvc.perform(delete("/painel/produtos/1")
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isNotFound()); 
    }

    @Test
    void putAutenticadoComCsrf_devePassarPeloSecurity() throws Exception {
        mockMvc.perform(put("/painel/estabelecimento")
                        .cookie(userCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); 
    }

    @Test
    void postAutenticadoComCsrf_devePassarPeloSecurity() throws Exception {
        mockMvc.perform(post("/painel/produtos")
                        .cookie(userCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()); 
    }

    @Test
    void uploadAutenticadoComCsrf_devePassarPeloSecurity() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", "fake image".getBytes());
        mockMvc.perform(multipart("/painel/storage/upload")
                        .file(file)
                        .cookie(userCookie)
                        .with(csrf()))
                .andExpect(status().isBadRequest()); 
    }
}
