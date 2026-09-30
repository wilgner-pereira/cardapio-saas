package com.wilgner.cardapio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

@Service
public class StorageImageLifecycle {
    private static final Logger log = LoggerFactory.getLogger(StorageImageLifecycle.class);
    private final JdbcTemplate jdbc;
    private final SupabaseStorageService storage;
    private final TransactionTemplate cleanupTransaction;

    public StorageImageLifecycle(JdbcTemplate jdbc, SupabaseStorageService storage,
                                 PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.cleanupTransaction = new TransactionTemplate(transactions);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void discardUnused(long tenantId, String url) {
        jdbc.queryForList("select id from estabelecimento where id=? for update", tenantId);
        replace(tenantId, url, null);
    }

    @Transactional
    public void replace(long tenantId, String previousUrl, String nextUrl) {
        String nextKey = storage.managedObjectKey(nextUrl);
        if (nextKey != null) {
            // Tenant mutations and cleanup acquire the tenant lock before this ledger lock.
            var available = jdbc.queryForList("select id from storage_object where estabelecimento_id=? "
                    + "and object_key=? and public_url=? and status='STORED' for update", tenantId, nextKey, nextUrl);
            if (available.isEmpty()) {
                throw new IllegalArgumentException("Imagem não pertence ao estabelecimento ou não está disponível");
            }
        }
        String previousKey = storage.managedObjectKey(previousUrl);
        if (previousKey == null || Objects.equals(previousKey, nextKey)) return;
        var owned = jdbc.queryForList("select id from storage_object where estabelecimento_id=? "
                + "and object_key=? and status='STORED'", Long.class, tenantId, previousKey);
        if (owned.isEmpty()) return; // External images and unowned objects are never deleted.
        long objectId = owned.get(0);
        Integer queued = jdbc.queryForObject("select count(*) from storage_deletion where storage_object_id=?", Integer.class, objectId);
        if (queued == 0) jdbc.update("insert into storage_deletion (storage_object_id) values (?)", objectId);
        else jdbc.update("update storage_deletion set next_attempt_at=CURRENT_TIMESTAMP where storage_object_id=?", objectId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deletePending(objectId); }
        });
    }

    @Scheduled(fixedDelayString = "${app.storage.cleanup-interval-ms:60000}",
            initialDelayString = "${app.storage.cleanup-interval-ms:60000}")
    public void retryPending() {
        try {
            var ids = jdbc.queryForList("select storage_object_id from storage_deletion "
                    + "where next_attempt_at<=CURRENT_TIMESTAMP order by next_attempt_at,storage_object_id limit 20", Long.class);
            ids.forEach(this::deletePending);
        } catch (RuntimeException failure) {
            log.warn("Falha na consulta de exclusões pendentes motivo={}", failure.getClass().getSimpleName());
        }
    }

    private void deletePending(long objectId) {
        try {
            cleanupTransaction.executeWithoutResult(status -> {
                var owners = jdbc.queryForList("select estabelecimento_id from storage_object where id=?", Long.class, objectId);
                if (owners.isEmpty()) return;
                Long tenantId = owners.get(0);
                if (tenantId != null) jdbc.queryForList("select id from estabelecimento where id=? for update", tenantId);
                var pending = jdbc.queryForList("select storage_object_id from storage_deletion "
                        + "where storage_object_id=? and next_attempt_at<=CURRENT_TIMESTAMP for update", objectId);
                if (pending.isEmpty()) return;
                var objects = jdbc.query("select object_key,public_url,status from storage_object where id=? for update",
                        (row, index) -> new StoredObject(row.getString(1), row.getString(2), row.getString(3)), objectId);
                if (objects.isEmpty()) return;
                StoredObject object = objects.get(0);
                if ("DELETED".equals(object.status())) {
                    jdbc.update("delete from storage_deletion where storage_object_id=?", objectId);
                    return;
                }
                Long references = jdbc.queryForObject("select (select count(*) from produtos where imagem_url=?) "
                        + "+ (select count(*) from estabelecimento where logo_url=? or hero_image_url=?)",
                        Long.class, object.url(), object.url(), object.url());
                if (references > 0) {
                    postpone(objectId); // A shared image is removed only after its final reference changes.
                    return;
                }
                if (!"STORED".equals(object.status()) || !Objects.equals(object.key(), storage.managedObjectKey(object.url()))) {
                    postpone(objectId);
                    return;
                }
                try {
                    storage.deleteFile(object.key()).block();
                    jdbc.update("update storage_object set status='DELETED',updated_at=CURRENT_TIMESTAMP where id=?", objectId);
                    jdbc.update("delete from storage_deletion where storage_object_id=?", objectId);
                } catch (RuntimeException failure) {
                    postpone(objectId);
                    log.warn("Exclusão de imagem pendente objectId={} motivo={}", objectId, failure.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException failure) {
            // The queue was committed with the new reference; retry survives a DB failure/restart.
            log.warn("Falha na conciliação da exclusão objectId={} motivo={}", objectId, failure.getClass().getSimpleName());
        }
    }

    private void postpone(long objectId) {
        jdbc.update("update storage_deletion set attempts=attempts+1,next_attempt_at=? where storage_object_id=?",
                Timestamp.from(Instant.now().plusSeconds(60)), objectId);
    }

    private record StoredObject(String key, String url, String status) {}
}
