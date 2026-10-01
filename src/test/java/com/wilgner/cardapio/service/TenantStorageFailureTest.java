package com.wilgner.cardapio.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.wilgner.cardapio.exception.ExternalServiceException;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TenantStorageFailureTest {
    @Test void optimizedBytesDetermineQuotaAndStorageContentType() throws Exception {
        var remote = mock(SupabaseStorageService.class);
        var quota = mock(StorageQuotaService.class);
        byte[] optimized = SupabaseStorageServiceTest.fixture("product.webp");
        var file = SupabaseStorageServiceTest.file(optimized);
        when(remote.validateFile(file, ImageUploadProfile.PRODUCT)).thenReturn(optimized);
        when(remote.publicUrl(anyString())).thenReturn("https://storage.example.test/object");
        when(remote.uploadBytes(anyString(), eq("image/webp"), eq(optimized))).thenReturn(Mono.empty());
        new TenantStorageService(remote, quota).upload(1L, file, ImageUploadProfile.PRODUCT);
        var order = inOrder(remote, quota);
        order.verify(remote).validateFile(file, ImageUploadProfile.PRODUCT);
        order.verify(remote).publicUrl(anyString());
        order.verify(quota).reserve(eq(1L), anyString(), anyString(), eq((long) optimized.length));
        order.verify(remote).uploadBytes(anyString(), eq("image/webp"), eq(optimized));
        order.verify(quota).mark(anyString(), eq("STORED"));
    }

    @Test void invalidImageNeverReservesQuotaOrContactsStorage() throws Exception {
        var remote = mock(SupabaseStorageService.class);
        var quota = mock(StorageQuotaService.class);
        when(remote.validateFile(any(), any())).thenThrow(new IllegalArgumentException("Imagem inválida"));
        assertThatThrownBy(() -> new TenantStorageService(remote, quota).upload(1L,
                SupabaseStorageServiceTest.file(new byte[12]), ImageUploadProfile.HERO)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(quota);
        verify(remote, never()).uploadBytes(anyString(), anyString(), any());
    }
    @Test void databaseFailureAfterRemoteSuccessAttemptsCleanupAndNeverReportsSuccess() throws Exception {
        var remote = mock(SupabaseStorageService.class);
        var quota = mock(StorageQuotaService.class);
        when(remote.validateFile(any(), any())).thenReturn(new byte[12]);
        when(remote.publicUrl(anyString())).thenReturn("https://storage.example.test/object");
        when(remote.uploadBytes(anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(remote.deleteFile(anyString())).thenReturn(Mono.empty());
        doThrow(new RuntimeException("database unavailable")).when(quota).mark(anyString(), eq("STORED"));
        var service = new TenantStorageService(remote, quota);
        assertThatThrownBy(() -> service.upload(1L, new MockMultipartFile("file", "optimized.webp", "image/webp", new byte[12]), ImageUploadProfile.PRODUCT))
                .isInstanceOf(ExternalServiceException.class).hasMessage("Falha ao enviar imagem para o storage");
        var order = inOrder(quota, remote);
        order.verify(quota).reserve(eq(1L), anyString(), anyString(), eq(12L));
        order.verify(remote).uploadBytes(anyString(), anyString(), any());
        order.verify(quota).mark(anyString(), eq("STORED"));
        order.verify(remote).deleteFile(anyString());
        order.verify(quota).mark(anyString(), eq("DELETED"));
    }
}
