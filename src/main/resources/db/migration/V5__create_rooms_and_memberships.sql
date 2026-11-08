CREATE TABLE rooms (
    id uuid PRIMARY KEY,
    creator_id uuid NOT NULL REFERENCES users(id),
    name varchar(60) NOT NULL CHECK (length(btrim(name)) > 0),
    is_public boolean NOT NULL,
    capacity integer NOT NULL CHECK (capacity BETWEEN 2 AND 8),
    center geography(Point,4326) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    closed_at timestamptz
);
CREATE INDEX rooms_center_idx ON rooms USING gist(center);
CREATE TABLE room_memberships (
    id uuid PRIMARY KEY,
    room_id uuid NOT NULL REFERENCES rooms(id),
    user_id uuid NOT NULL REFERENCES users(id),
    joined_at timestamptz NOT NULL,
    heartbeat_at timestamptz NOT NULL,
    left_at timestamptz
);
CREATE UNIQUE INDEX one_room_per_user ON room_memberships(user_id) WHERE left_at IS NULL;
CREATE INDEX room_members_idx ON room_memberships(room_id) WHERE left_at IS NULL;
CREATE TABLE voice_leases (
    identity uuid PRIMARY KEY,
    membership_id uuid NOT NULL REFERENCES room_memberships(id),
    token_hash char(64) NOT NULL REFERENCES auth_sessions(token_hash),
    UNIQUE(membership_id,token_hash)
);
