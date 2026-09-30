package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.ExternalServiceException;
import com.wilgner.cardapio.exception.StorageWriteRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.UUID;

@Service
public class TenantStorageService {
    private final SupabaseStorageService storage;
    private final StorageQuotaService quota;

    public TenantStorageService(SupabaseStorageService storage, StorageQuotaService quota) {
        this.storage = storage;
        this.quota = quota;
    }

    public String upload(long tenantId, MultipartFile file) throws IOException {
        byte[] bytes = storage.validateFile(file);
        String key = UUID.randomUUID().toString(); // No original filename/personal data in paths.
        String url = storage.publicUrl(key);
        quota.reserve(tenantId, key, url, bytes.length);
        boolean remoteCompleted = false;
        try {
            storage.uploadBytes(key, file.getContentType(), bytes).block();
            remoteCompleted = true;
            quota.mark(key, "STORED");
            return url;
        } catch (RuntimeException failure) {
            // A timed-out write may finish AFTER a cleanup request. Keep that charge
            // even when immediate deletion succeeds; an operator must reconcile it.
            try {
                if (failure instanceof StorageWriteRejectedException) {
                    quota.mark(key, "DELETED"); // Explicit refusal; no remote write to undo.
                } else {
                    storage.deleteFile(key).block();
                    quota.mark(key, remoteCompleted ? "DELETED" : "UNCERTAIN");
                }
            } catch (RuntimeException cleanupFailure) {
                try { quota.mark(key, "UNCERTAIN"); }
                catch (RuntimeException databaseFailure) { /* RESERVED remains charged and discoverable. */ }
            }
            throw new ExternalServiceException("Falha ao enviar imagem para o storage");
        }
    }
}
