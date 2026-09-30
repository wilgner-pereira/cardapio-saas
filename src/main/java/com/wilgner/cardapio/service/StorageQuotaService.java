package com.wilgner.cardapio.service;

import com.wilgner.cardapio.exception.StorageQuotaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageQuotaService {
    private final JdbcTemplate jdbc;
    private final long quota;

    public StorageQuotaService(JdbcTemplate jdbc, @Value("${app.storage.tenant-quota-bytes:104857600}") long quota) {
        if (quota <= 0) throw new IllegalArgumentException("Cota de storage deve ser positiva");
        this.jdbc = jdbc;
        this.quota = quota;
    }

    // Commit reservation before any network I/O. Lock is shared by all app instances.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(long tenantId, String key, String publicUrl, long bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("Arquivo vazio");
        Boolean inventoryRequired = jdbc.queryForObject("select storage_inventory_required from estabelecimento where id = ? for update", Boolean.class, tenantId);
        Long unknown = jdbc.queryForObject("select count(*) from storage_object where estabelecimento_id = ? and status = 'LEGACY'",
                Long.class, tenantId);
        if (Boolean.TRUE.equals(inventoryRequired) || (unknown != null && unknown > 0)) {
            throw new StorageQuotaException("Armazenamento pendente de conciliação. Contate o suporte.");
        }
        Long used = jdbc.queryForObject("select coalesce(sum(size_bytes), 0) from storage_object "
                + "where estabelecimento_id = ? and status <> 'DELETED'", Long.class, tenantId);
        if (bytes > quota || used > quota - bytes) {
            throw new StorageQuotaException("Cota de armazenamento do estabelecimento excedida.");
        }
        jdbc.update("insert into storage_object (estabelecimento_id, object_key, public_url, size_bytes, status) values (?, ?, ?, ?, 'RESERVED')",
                tenantId, key, publicUrl, bytes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void mark(String key, String status) {
        if (!java.util.Set.of("STORED", "UNCERTAIN", "DELETED").contains(status)) {
            throw new IllegalArgumentException("Estado inválido");
        }
        int changed = jdbc.update("update storage_object set status = ?, updated_at = CURRENT_TIMESTAMP where object_key = ?", status, key);
        if (changed != 1) throw new IllegalStateException("Registro de storage ausente");
    }
}
