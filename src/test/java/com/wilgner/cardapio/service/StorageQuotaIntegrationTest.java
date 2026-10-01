package com.wilgner.cardapio.service;

import com.wilgner.cardapio.model.entity.*;
import com.wilgner.cardapio.repository.*;
import com.wilgner.cardapio.security.TokenService;
import com.wilgner.cardapio.exception.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage.tenant-quota-bytes=24")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageQuotaIntegrationTest {
    static final String URL = "jdbc:h2:file:./target/storage-quota-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
    static long persistedTenant;
    static ApplicationContext previous;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { registry.add("spring.datasource.url", () -> URL); }
    @Autowired StorageQuotaService quota;
    @Autowired TenantStorageService uploads;
    @Autowired JdbcTemplate jdbc;
    @Autowired EstabelecimentoRepository establishments;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenService tokens;
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @MockBean SupabaseStorageService remote;

    @BeforeEach void remote() throws Exception {
        when(remote.validateFile(any(), any())).thenReturn(new byte[12]);
        when(remote.publicUrl(anyString())).thenAnswer(call -> "https://storage.example.test/" + call.getArgument(0));
        when(remote.uploadBytes(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(remote.deleteFile(anyString())).thenReturn(Mono.empty());
    }

    @Test @Order(1) @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void belowQuotaIsPersistedBeforeRestart() throws Exception {
        persistedTenant = tenant();
        uploads.upload(persistedTenant, file(), ImageUploadProfile.PRODUCT);
        uploads.upload(persistedTenant, file(), ImageUploadProfile.PRODUCT);
        assertThat(used(persistedTenant)).isEqualTo(24);
        previous = context;
    }

    @Test @Order(2)
    void reopenedContextKeepsQuotaAndRejectsOverLimit() {
        assertThat(context).isNotSameAs(previous);
        assertThat(used(persistedTenant)).isEqualTo(24);
        assertThatThrownBy(() -> uploads.upload(persistedTenant, file(), ImageUploadProfile.PRODUCT)).isInstanceOf(StorageQuotaException.class);
        verify(remote, never()).uploadBytes(anyString(), anyString(), any());
    }

    @Test void tenantsHaveIndependentQuota() throws Exception {
        long first = tenant(), second = tenant();
        uploads.upload(first, file(), ImageUploadProfile.PRODUCT);
        uploads.upload(first, file(), ImageUploadProfile.PRODUCT);
        uploads.upload(second, file(), ImageUploadProfile.PRODUCT);
        assertThat(used(first)).isEqualTo(24);
        assertThat(used(second)).isEqualTo(12);
    }

    @Test void concurrentReservationsCannotExceedQuota() throws Exception {
        long tenant = tenant();
        uploads.upload(tenant, file(), ImageUploadProfile.PRODUCT);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                start.await(10, TimeUnit.SECONDS);
                try { uploads.upload(tenant, file(), ImageUploadProfile.PRODUCT); return true; }
                catch (StorageQuotaException e) { return false; }
            };
            var first = pool.submit(attempt); var second = pool.submit(attempt);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(used(tenant)).isEqualTo(24);
    }

    @Test void explicitlyRejectedUploadReleasesReservation() throws Exception {
        long tenant = tenant();
        when(remote.uploadBytes(anyString(), anyString(), any())).thenReturn(Mono.error(new StorageWriteRejectedException()));
        assertThatThrownBy(() -> uploads.upload(tenant, file(), ImageUploadProfile.PRODUCT)).isInstanceOf(ExternalServiceException.class);
        assertThat(used(tenant)).isZero();
        assertThat(jdbc.queryForObject("select status from storage_object where estabelecimento_id=?", String.class, tenant)).isEqualTo("DELETED");
        verify(remote, never()).deleteFile(anyString());
    }

    @Test void timedOutWriteStaysChargedEvenWhenImmediateCleanupSucceeds() throws Exception {
        long tenant = tenant();
        when(remote.uploadBytes(anyString(), anyString(), any())).thenReturn(Mono.error(new RuntimeException("timeout")));
        assertThatThrownBy(() -> uploads.upload(tenant, file(), ImageUploadProfile.PRODUCT)).isInstanceOf(ExternalServiceException.class);
        assertThat(used(tenant)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select status from storage_object where estabelecimento_id=?", String.class, tenant)).isEqualTo("UNCERTAIN");
        verify(remote).deleteFile(anyString());
    }

    @Test void ambiguousUploadRemainsChargedAndDiscoverable() throws Exception {
        long tenant = tenant();
        when(remote.uploadBytes(anyString(), anyString(), any())).thenReturn(Mono.error(new RuntimeException("timeout")));
        when(remote.deleteFile(anyString())).thenReturn(Mono.error(new RuntimeException("offline")));
        assertThatThrownBy(() -> uploads.upload(tenant, file(), ImageUploadProfile.PRODUCT)).isInstanceOf(ExternalServiceException.class);
        assertThat(used(tenant)).isEqualTo(12);
        assertThat(jdbc.queryForObject("select status from storage_object where estabelecimento_id=?", String.class, tenant)).isEqualTo("UNCERTAIN");
    }

    @Test void inventoryGateAndUnknownLegacyBytesBlockUploads() {
        long tenant = tenant();
        jdbc.update("update estabelecimento set storage_inventory_required=true where id=?", tenant);
        assertThatThrownBy(() -> quota.reserve(tenant, "blocked", "https://example.test/x", 1)).isInstanceOf(StorageQuotaException.class);
        jdbc.update("update estabelecimento set storage_inventory_required=false where id=?", tenant);
        jdbc.update("insert into storage_object (estabelecimento_id, public_url, status) values (?, 'https://example.test/old', 'LEGACY')", tenant);
        assertThatThrownBy(() -> quota.reserve(tenant, "blocked", "https://example.test/x", 1)).isInstanceOf(StorageQuotaException.class);
    }

    @Test void authenticatedTenantOwnsChargeAndQuotaResponseIsConflict() throws Exception {
        long first = tenant(), second = tenant();
        var user = new Usuario("upload-" + UUID.randomUUID(), "unused", UUID.randomUUID() + "@example.test",
                establishments.findById(first).orElseThrow());
        user.setRoles(Set.of(roles.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow()));
        user = users.save(user);
        var cookie = new Cookie("access_token", tokens.generateAccessToken(user));
        mvc.perform(multipart("/painel/storage/upload").file(file()).cookie(cookie).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/painel/storage/upload").file(file()).param("type", "logo").cookie(cookie).with(csrf())).andExpect(status().isBadRequest());
        assertThat(used(first)).isZero();
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart("/painel/storage/upload").file(file()).param("type", "product").param("estabelecimento_id", Long.toString(second))
                    .cookie(cookie).with(csrf())).andExpect(status().isOk());
        }
        mvc.perform(multipart("/painel/storage/upload").file(file()).param("type", "product").cookie(cookie).with(csrf())).andExpect(status().isConflict());
        assertThat(used(first)).isEqualTo(24);
        assertThat(used(second)).isZero();
    }

    @Test void removingTenantRetainsObjectForOrphanInventory() throws Exception {
        long tenant = tenant();
        String url = uploads.upload(tenant, file(), ImageUploadProfile.PRODUCT);
        establishments.deleteById(tenant);
        assertThat(jdbc.queryForObject("select count(*) from storage_object where public_url=? and estabelecimento_id is null", Integer.class, url)).isEqualTo(1);
    }

    long tenant() {
        var e = new Estabelecimento(); e.setNome("Quota test"); e.setSlug("quota-" + UUID.randomUUID());
        return establishments.save(e).getId();
    }
    long used(long tenant) {
        return jdbc.queryForObject("select coalesce(sum(size_bytes),0) from storage_object where estabelecimento_id=? and status <> 'DELETED'", Long.class, tenant);
    }
    MockMultipartFile file() { return new MockMultipartFile("file", "optimized.webp", "image/webp", new byte[12]); }
}
