CREATE TABLE refresh_session (
    id VARCHAR(36) PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_refresh_session_user ON refresh_session(usuario_id);
CREATE INDEX idx_refresh_session_expiration ON refresh_session(expires_at);
