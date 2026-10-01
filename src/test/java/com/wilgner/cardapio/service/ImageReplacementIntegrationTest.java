package com.wilgner.cardapio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilgner.cardapio.model.entity.*;
import com.wilgner.cardapio.repository.*;
import com.wilgner.cardapio.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.storage.cleanup-interval-ms=3600000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageReplacementIntegrationTest {
    static final String PREFIX="http://localhost:54321/storage/v1/object/public/cardapio-imagens/";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EstabelecimentoRepository establishments;
    @Autowired UsuarioRepository users;
    @Autowired RoleRepository roles;
    @Autowired TenantStorageService uploads;
    @Autowired StorageImageLifecycle lifecycle;
    @Autowired PlatformTransactionManager transactions;
    @MockBean SupabaseStorageService remote;
    Estabelecimento tenant;
    CustomUserDetails owner;

    @BeforeEach void setup() throws Exception {
        when(remote.validateFile(any(), any())).thenReturn(new byte[12]);
        when(remote.publicUrl(anyString())).thenAnswer(call->PREFIX+call.getArgument(0));
        when(remote.managedObjectKey(anyString())).thenAnswer(call->{
            String url=call.getArgument(0);return url.startsWith(PREFIX)?url.substring(PREFIX.length()).split("\\?",2)[0]:null;
        });
        when(remote.uploadBytes(anyString(),anyString(),any())).thenReturn(Mono.empty());
        when(remote.deleteFile(anyString())).thenReturn(Mono.empty());
        String slug="images-"+UUID.randomUUID();
        tenant=establishments.save(new Estabelecimento("Pizzaria testes",slug,"Cardápio"));
        var user=new Usuario(slug,"test-only",slug+"@example.test",tenant);
        user.setRoles(Set.of(roles.findByRoleName("ROLE_TENANT_ADMIN").orElseThrow()));
        owner=new CustomUserDetails(users.save(user));
    }

    @Test void productReplacementDeletesOldObjectAfterTheNewReferenceCommitsAndReleasesQuota() throws Exception {
        String old=upload(tenant.getId()), next=upload(tenant.getId());
        long product=createProduct(old);
        when(remote.deleteFile(key(old))).thenAnswer(call->{
            assertThat(jdbc.queryForObject("select imagem_url from produtos where id=?",String.class,product)).isEqualTo(next);
            return Mono.empty();
        });
        updateProduct(product,next);
        verify(remote).deleteFile(key(old));
        assertThat(state(old)).isEqualTo("DELETED");
        assertThat(state(next)).isEqualTo("STORED");
        assertThat(used()).isEqualTo(12);
        assertThat(queued(old)).isZero();
    }

    @Test void editingWithoutChangingTheImageKeepsTheFile() throws Exception {
        String image=upload(tenant.getId());long product=createProduct(image);
        updateProduct(product,image);
        verify(remote,never()).deleteFile(anyString());
        assertThat(state(image)).isEqualTo("STORED");
        assertThat(queued(image)).isZero();
    }

    @Test void coverReplacementAndRemovalDeleteOnlyThePreviousCover() throws Exception {
        String old=upload(tenant.getId()), next=upload(tenant.getId());
        saveCover(old);saveCover(next);
        assertThat(establishments.findById(tenant.getId()).orElseThrow().getHeroImageUrl()).isEqualTo(next);
        assertThat(state(old)).isEqualTo("DELETED");
        verify(remote).deleteFile(key(old));
        saveCover("");
        assertThat(establishments.findById(tenant.getId()).orElseThrow().getHeroImageUrl()).isNull();
        assertThat(state(next)).isEqualTo("DELETED");
        verify(remote).deleteFile(key(next));
        assertThat(used()).isZero();
    }

    @Test void deletionFailureKeepsTheSavedCoverAndDurableRetryThenReleasesQuotaOnSuccess() throws Exception {
        String old=upload(tenant.getId()), next=upload(tenant.getId());saveCover(old);
        when(remote.deleteFile(key(old))).thenReturn(Mono.error(new RuntimeException("offline")));
        saveCover(next);
        assertThat(establishments.findById(tenant.getId()).orElseThrow().getHeroImageUrl()).isEqualTo(next);
        assertThat(state(old)).isEqualTo("STORED");
        assertThat(used()).isEqualTo(24);
        assertThat(queued(old)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from storage_deletion where storage_object_id=(select id from storage_object where public_url=?)",Integer.class,old)).isEqualTo(1);
        when(remote.deleteFile(key(old))).thenReturn(Mono.empty());
        jdbc.update("update storage_deletion set next_attempt_at=CURRENT_TIMESTAMP where storage_object_id=(select id from storage_object where public_url=?)",old);
        lifecycle.retryPending();
        verify(remote,times(2)).deleteFile(key(old));
        assertThat(state(old)).isEqualTo("DELETED");
        assertThat(queued(old)).isZero();
        assertThat(used()).isEqualTo(12);
    }

    @Test void rolledBackReplacementKeepsThePreviousImageAndDoesNotSendAnyDeletion() throws Exception {
        String old=upload(tenant.getId()), next=upload(tenant.getId());long product=createProduct(old);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.queryForList("select id from estabelecimento where id=? for update",tenant.getId());
            lifecycle.replace(tenant.getId(),old,next);
            jdbc.update("update produtos set imagem_url=? where id=?",next,product);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select imagem_url from produtos where id=?",String.class,product)).isEqualTo(old);
        assertThat(queued(old)).isZero();
        assertThat(state(old)).isEqualTo("STORED");
        verify(remote,never()).deleteFile(anyString());
    }

    @Test void sharedImageIsRemovedOnlyWhenTheLastProductOrCoverStopsUsingIt() throws Exception {
        String shared=upload(tenant.getId()), next=upload(tenant.getId());
        long product=createProduct(shared);saveCover(shared);
        updateProduct(product,next);
        verify(remote,never()).deleteFile(key(shared));
        assertThat(state(shared)).isEqualTo("STORED");
        saveCover("");
        verify(remote).deleteFile(key(shared));
        assertThat(state(shared)).isEqualTo("DELETED");
        assertThat(queued(shared)).isZero();
    }

    @Test void deletingProductAlsoDeletesItsUnusedImage() throws Exception {
        String image=upload(tenant.getId());long product=createProduct(image);
        mvc.perform(delete("/painel/produtos/"+product).with(user(owner)).with(csrf())).andExpect(status().isNoContent());
        verify(remote).deleteFile(key(image));
        assertThat(state(image)).isEqualTo("DELETED");
    }

    @Test void anotherTenantsImageCannotBeAttachedOrDeletedIncludingThroughAUrlAlias() throws Exception {
        var other=establishments.save(new Estabelecimento("Outro restaurante","other-"+UUID.randomUUID(),"Menu"));
        String foreign=upload(other.getId()), old=upload(tenant.getId());long product=createProduct(old);
        for(String url:new String[]{foreign,foreign+"?alias=1"}){
            mvc.perform(put("/painel/produtos/"+product).with(user(owner)).with(csrf())
                    .contentType("application/json").content(productPayload(url))).andExpect(status().isBadRequest());
        }
        mvc.perform(delete("/painel/storage/unused-image").with(user(owner)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("url",foreign))))
                .andExpect(status().isNoContent());
        assertThat(state(foreign)).isEqualTo("STORED");
        assertThat(state(old)).isEqualTo("STORED");
        verify(remote,never()).deleteFile(anyString());
    }

    @Test void unusedUploadCanBeDiscardedButAnAttachedImageIsProtected() throws Exception {
        String unused=upload(tenant.getId()), used=upload(tenant.getId());createProduct(used);
        for(String url:new String[]{unused,used}){
            mvc.perform(delete("/painel/storage/unused-image").with(user(owner)).with(csrf())
                    .contentType("application/json").content(json.writeValueAsString(Map.of("url",url))))
                    .andExpect(status().isNoContent());
        }
        verify(remote).deleteFile(key(unused));
        verify(remote,never()).deleteFile(key(used));
        assertThat(state(unused)).isEqualTo("DELETED");
        assertThat(state(used)).isEqualTo("STORED");
        mvc.perform(delete("/painel/storage/unused-image").with(user(owner))
                .contentType("application/json").content(json.writeValueAsString(Map.of("url",used))))
                .andExpect(status().isForbidden());
    }

    @Test void externalImagesAreNeverDeletedFromTheConfiguredBucket() throws Exception {
        long product=createProduct("https://example.com/external.jpg");
        updateProduct(product,upload(tenant.getId()));
        verify(remote,never()).deleteFile(anyString());
    }

    String upload(long id) throws Exception {return uploads.upload(id,new MockMultipartFile("file","optimized.webp","image/webp",new byte[12]),ImageUploadProfile.HERO);}
    String key(String url){return url.substring(PREFIX.length());}
    String state(String url){return jdbc.queryForObject("select status from storage_object where public_url=?",String.class,url);}
    long used(){return jdbc.queryForObject("select coalesce(sum(size_bytes),0) from storage_object where estabelecimento_id=? and status<>'DELETED'",Long.class,tenant.getId());}
    int queued(String url){return jdbc.queryForObject("select count(*) from storage_deletion where storage_object_id=(select id from storage_object where public_url=?)",Integer.class,url);}
    String productPayload(String url)throws Exception{return json.writeValueAsString(Map.of("nome","Pizza testes","descricao","Pizza para testes","preco",12.5,"categoria","Pizzas","imageUrl",url));}
    long createProduct(String url)throws Exception{
        var result=mvc.perform(post("/painel/produtos").with(user(owner)).with(csrf()).contentType("application/json").content(productPayload(url)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
    void updateProduct(long id,String url)throws Exception{
        mvc.perform(put("/painel/produtos/"+id).with(user(owner)).with(csrf()).contentType("application/json").content(productPayload(url))).andExpect(status().isOk());
    }
    void saveCover(String url)throws Exception{
        mvc.perform(put("/painel/estabelecimento").with(user(owner)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("nome","Pizzaria testes","heroImageUrl",url)))).andExpect(status().isOk());
    }
}
