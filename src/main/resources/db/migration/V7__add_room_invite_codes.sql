ALTER TABLE rooms ADD COLUMN invite_code varchar(32);
-- Preserve every existing room without collisions or a change to its UUID invitation.
UPDATE rooms SET invite_code = upper(replace(id::text, '-', ''));
ALTER TABLE rooms ALTER COLUMN invite_code SET NOT NULL;
CREATE UNIQUE INDEX rooms_invite_code_unique ON rooms(invite_code);
