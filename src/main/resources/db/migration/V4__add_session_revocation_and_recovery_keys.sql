ALTER TABLE auth_sessions ADD COLUMN revoked_at timestamptz;

CREATE TABLE vehicle_recovery_keys (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    key_hash char(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at)
);
