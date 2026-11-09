ALTER TABLE trip_sessions ADD COLUMN revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0);
