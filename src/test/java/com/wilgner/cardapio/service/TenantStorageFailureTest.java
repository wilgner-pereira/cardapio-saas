package com.wilgner.cardapio.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.wilgner.cardapio.exception.ExternalServiceException;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TenantStorageFailureTest {
    @Test void databaseFailureAfterRemoteSuccessAttemptsCleanupAndNeverReportsSuccess() throws Exception {
        var remote = mock(SupabaseStorageService.class);
        var quota = mock(StorageQuotaService.class);
        when(remote.validateFile(any())).thenReturn(new byte[12]);
        when(remote.publicUrl(anyString())).thenReturn("https://storage.example.test/object");
        when(remote.uploadBytes(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(remote.deleteFile(anyString())).thenReturn(Mono.empty());
        doThrow(new RuntimeException("database unavailable")).when(quota).mark(anyString(), eq("STORED"));
        var service = new TenantStorageService(remote, quota);
        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile("file", "x.png", "image/png", new byte[12])))
                .isInstanceOf(ExternalServiceException.class).hasMessage("Falha ao enviar imagem para o storage");
        var order = inOrder(quota, remote);
        order.verify(quota).reserve(eq(1L), anyString(), anyString(), eq(12L));
        order.verify(remote).uploadBytes(anyString(), anyString(), any());
        order.verify(quota).mark(anyString(), eq("STORED"));
        order.verify(remote).deleteFile(anyString());
        order.verify(quota).mark(anyString(), eq("DELETED"));
    }
}
