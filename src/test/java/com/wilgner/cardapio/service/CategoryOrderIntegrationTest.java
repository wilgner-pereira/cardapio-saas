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
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryOrderIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EstabelecimentoRepository establishments;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    @Autowired ProdutoRepository products;
    @Autowired PublicProdutoServiceImpl publicService;
    Estabelecimento establishment;
    CustomUserDetails owner;

    @BeforeEach void setup() {
        String name = "order-" + UUID.randomUUID();
        establishment = establishments.save(new Estabelecimento(name, name, "Menu"));
        Usuario account = new Usuario(name, "test-only", name + "@test.example", establishment);
        account.setRoles(Set.of(roles.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow()));
        owner = new CustomUserDetails(users.save(account));
        products.save(new Produto(null, "Entrada 1", "Descricao", BigDecimal.TEN, true, "Entradas", null, 0, 0, establishment, account));
        products.save(new Produto(null, "Entrada 2", "Descricao", BigDecimal.TEN, true, "Entradas", null, 1, 0, establishment, account));
        products.save(new Produto(null, "Bebida", "Descricao", BigDecimal.TEN, true, "Bebidas", null, 0, 1, establishment, account));
    }

    void swap(String source, String target) throws Exception {
        mvc.perform(patch("/painel/categorias/ordem").with(user(owner)).with(csrf())
                .contentType("application/json").content("{\"categoria\":\"" + source + "\",\"categoriaAlvo\":\"" + target + "\"}"))
                .andExpect(status().isOk());
    }

    @Test void moveUpAndDownPersistsAndPublicAndAdminReadTheSameOrder() throws Exception {
        swap("Bebidas", "Entradas");
        assertThat(publicService.listarProdutoDinamico(establishment.getSlug(), null))
                .extracting(dto -> dto.nome()).containsExactly("Bebida", "Entrada 1", "Entrada 2");
        mvc.perform(get("/painel/produtos").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].categoria").value("Bebidas"));
        assertThat(products.findByEstabelecimentoOrderByCategoriaAndOrdem(establishment))
                .extracting(Produto::getOrdem).containsExactly(0, 0, 1);
        swap("Bebidas", "Entradas");
        assertThat(publicService.listarProdutoDinamico(establishment.getSlug(), null))
                .extracting(dto -> dto.nome()).containsExactly("Entrada 1", "Entrada 2", "Bebida");
    }

    @Test void sameCategoryIsSafeAndMissingCategoryDoesNotPartiallyUpdate() throws Exception {
        swap("Entradas", "Entradas");
        mvc.perform(patch("/painel/categorias/ordem").with(user(owner)).with(csrf())
                .contentType("application/json").content("{\"categoria\":\"Entradas\",\"categoriaAlvo\":\"Ausente\"}"))
                .andExpect(status().isNotFound());
        assertThat(products.findByEstabelecimentoOrderByCategoriaAndOrdem(establishment))
                .extracting(Produto::getCategoriaOrdem).containsExactly(0, 0, 1);
    }

    @Test void repairsDuplicateCategoryRanksWithoutChangingProductRanks() throws Exception {
        var existing = products.findByEstabelecimento(establishment);
        existing.forEach(product -> product.setCategoriaOrdem(0));
        products.saveAll(existing);
        swap("Entradas", "Bebidas");
        assertThat(products.findByEstabelecimentoOrderByCategoriaAndOrdem(establishment))
                .extracting(Produto::getCategoriaOrdem).containsExactly(0, 0, 1);
    }

    @Test void requiresTenantRoleAndCsrf() throws Exception {
        String body = "{\"categoria\":\"Entradas\",\"categoriaAlvo\":\"Bebidas\"}";
        mvc.perform(patch("/painel/categorias/ordem").with(user("reader").roles("USER")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/painel/categorias/ordem").with(user(owner))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(patch("/painel/categorias/ordem").with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
    }

    @Test void concurrentSwapsAreSerialized() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> movement = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                swap("Bebidas", "Entradas");
                return null;
            };
            var first = executor.submit(movement);
            var second = executor.submit(movement);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(products.findByEstabelecimentoOrderByCategoriaAndOrdem(establishment))
                .extracting(Produto::getCategoriaOrdem).containsExactly(0, 0, 1);
    }

    @Test void productReorderingRemainsIndependentAndMovingProductToNewCategoryAppendsIt() throws Exception {
        swap("Bebidas", "Entradas");
        var entry = products.findByEstabelecimentoAndCategoria(establishment, "Entradas").stream()
                .filter(product -> product.getOrdem() == 0).findFirst().orElseThrow();
        mvc.perform(patch("/painel/produtos/" + entry.getId() + "/ordem").with(user(owner)).with(csrf())
                .contentType("application/json").content("{\"ordem\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaOrdem").value(1));
        mvc.perform(put("/painel/produtos/" + entry.getId()).with(user(owner)).with(csrf())
                .contentType("application/json")
                .content("{\"nome\":\"Entrada 1\",\"descricao\":\"Descricao\",\"preco\":10,\"categoria\":\"Pizzas\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoriaOrdem").value(2))
                .andExpect(jsonPath("$.ordem").value(0));
        assertThat(publicService.listarProdutoDinamico(establishment.getSlug(), null))
                .extracting(dto -> dto.categoria()).containsExactly("Bebidas", "Entradas", "Pizzas");
    }
}
