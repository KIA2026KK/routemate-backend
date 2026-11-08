-- VIN is the business identity of a vehicle account, never a login credential.
CREATE TABLE vehicles (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    vin varchar(17) NOT NULL CHECK (vin ~ '^[A-HJ-NPR-Z0-9]{17}$' AND vin <> '00000000000000000'),
    source varchar(16) NOT NULL CHECK (source IN ('PLEOS_SDK', 'MOCK')),
    vin_access_type varchar(16),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (source, vin),
    UNIQUE (id, user_id),
    CHECK ((source = 'MOCK' AND vin_access_type IS NULL) OR
        (source = 'PLEOS_SDK' AND vin_access_type IN ('READ', 'READ_WRITE') AND vin_access_type IS NOT NULL)),
    CHECK (updated_at >= created_at)
);

-- Existing trips have no trustworthy VIN and remain unlinked; do not guess or delete them.
ALTER TABLE trip_sessions ADD COLUMN vehicle_id uuid;
ALTER TABLE trip_sessions ADD CONSTRAINT trip_vehicle_account_fk
    FOREIGN KEY (vehicle_id, user_id) REFERENCES vehicles(id, user_id);
