-- Keep cleanup durable across a remote failure or an application restart.
CREATE TABLE storage_deletion (
    storage_object_id BIGINT PRIMARY KEY REFERENCES storage_object(id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_storage_deletion_retry ON storage_deletion(next_attempt_at);
