package com.wilgner.cardapio.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_session")
public class RefreshSession {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshSession() {}

    public RefreshSession(String id, Long usuarioId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void rotate(String tokenHash) { this.tokenHash = tokenHash; }
    public void revoke(Instant now) { this.revokedAt = now; }
}
