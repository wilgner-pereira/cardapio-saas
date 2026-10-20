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

        Role userRole = roleRepository.findByRoleName("ROLE_TENANT_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setRoleName("ROLE_TENANT_ADMIN");
            return roleRepository.save(r);
        });

        Role adminRole = roleRepository.findByRoleName("ROLE_PLATFORM_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setRoleName("ROLE_PLATFORM_ADMIN");
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
    void validateToken_deveIncluirCacheControlNoStore() throws Exception {
        mockMvc.perform(get("/auth/validate").cookie(userCookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
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

    @Test
    void platformAdminCannotUseAnyTenantController() throws Exception {
        for (var request : List.of(get("/painel/produtos"), get("/painel/produtos/1"),
                post("/painel/produtos"), put("/painel/produtos/1"), delete("/painel/produtos/1"),
                patch("/painel/produtos/1/status"), patch("/painel/produtos/1/ordem"),
                patch("/painel/categorias/ordem"), get("/painel/estabelecimento"))) {
            mockMvc.perform(request.cookie(adminCookie).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(multipart("/painel/storage/upload")
                        .file(new MockMultipartFile("file", "test.png", "image/png", new byte[]{1}))
                        .cookie(adminCookie).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformRoutesRequirePlatformRoleAndLegacyRolesGrantNoAccess() throws Exception {
        mockMvc.perform(get("/plataforma/estabelecimentos").cookie(adminCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/plataforma/estabelecimentos").cookie(userCookie)).andExpect(status().isForbidden());
        for (String legacy : List.of("ADMIN", "USER")) {
            for (String path : List.of("/plataforma/estabelecimentos", "/painel/produtos")) {
                mockMvc.perform(get(path).with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("legacy").roles(legacy)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void newTenantRoleCannotReadEditOrDeleteAnotherTenantsProduct() throws Exception {
        String payload = """
                {"nome":"Original","descricao":"Teste de isolamento","preco":10,"categoria":"Teste","imageUrl":""}
                """;
        var created = mockMvc.perform(post("/painel/produtos").cookie(userCookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        Estabelecimento other = new Estabelecimento();
        other.setNome("Other tenant");
        other.setSlug("other-" + UUID.randomUUID());
        other.setAtivo(true);
        Usuario otherUser = new Usuario("other-" + UUID.randomUUID(), "test-only-hash",
                UUID.randomUUID() + "@example.test", estabelecimentoRepository.save(other));
        otherUser.setRoles(java.util.Set.of(roleRepository.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow()));
        usuarioRepository.save(otherUser);
        Cookie otherCookie = new Cookie("access_token", tokenService.generateAccessToken(otherUser));

        mockMvc.perform(get("/painel/produtos/" + id).cookie(otherCookie)).andExpect(status().isNotFound());
        mockMvc.perform(put("/painel/produtos/" + id).cookie(otherCookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload.replace("Original", "Changed")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/painel/produtos/" + id).cookie(otherCookie).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/painel/produtos/" + id).cookie(userCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Original"));
    }

    @Test
    void sameJwtUsesCurrentDatabaseRolesIncludingExplicitDualRole() throws Exception {
        Usuario user = usuarioRepository.findWithRolesAndEstabelecimentoByUsername(
                tokenService.validateAccessToken(userCookie.getValue())).orElseThrow();
        Role platform = roleRepository.findByRoleName("ROLE_PLATFORM_ADMIN").orElseThrow();
        Role tenant = roleRepository.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow();
        user.setRoles(java.util.Set.of(platform));
        usuarioRepository.save(user);
        mockMvc.perform(get("/painel/produtos").cookie(userCookie)).andExpect(status().isForbidden());
        mockMvc.perform(get("/plataforma/estabelecimentos").cookie(userCookie)).andExpect(status().isOk());
        user.setRoles(java.util.Set.of(platform, tenant));
        usuarioRepository.save(user);
        mockMvc.perform(get("/painel/produtos").cookie(userCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/plataforma/estabelecimentos").cookie(userCookie)).andExpect(status().isOk());
    }
}
