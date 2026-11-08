CREATE TABLE users (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL
);

CREATE TABLE auth_sessions (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX auth_sessions_user_idx ON auth_sessions(user_id);

CREATE TABLE trip_sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    destination_name varchar(120) NOT NULL CHECK (length(btrim(destination_name)) > 0),
    destination geography(Point, 4326) NOT NULL,
    started_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    ended_at timestamptz,
    CHECK (updated_at >= started_at),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE UNIQUE INDEX one_active_trip_per_user ON trip_sessions(user_id) WHERE ended_at IS NULL;
