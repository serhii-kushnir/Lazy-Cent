
-- ==================== 1. НОВА КОЛОНКА ====================
ALTER TABLE category ADD COLUMN name_lower VARCHAR(255);


-- ==================== 2. BACKFILL ====================
UPDATE category SET name_lower = LOWER(name);


-- ==================== 3. NOT NULL ====================
ALTER TABLE category ALTER COLUMN name_lower SET NOT NULL;


-- ==================== 4. UNIQUE INDEX ====================
-- Якщо тут впаде — у БД є case-insensitive дублікати (див. cleanup вище).
CREATE UNIQUE INDEX IF NOT EXISTS uk_category_user_name_lower
    ON category (user_id, name_lower);