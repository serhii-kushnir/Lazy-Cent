-- =====================================================================
-- V14 — optimistic locking для notes
-- =====================================================================

ALTER TABLE notes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;