-- =====================================================================
-- V13 — розшаринг нотаток з сім'єю
-- =====================================================================

ALTER TABLE notes ADD COLUMN share_with_family BOOLEAN NOT NULL DEFAULT FALSE;

-- Прискорює запит "нотатки сім'ї"
CREATE INDEX idx_notes_share_family ON notes (user_id, share_with_family);