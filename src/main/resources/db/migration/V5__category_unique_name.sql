-- =====================================================================
-- V5 — unique constraint на category (user_id, name)
-- =====================================================================
-- Захист від race condition: два паралельні addCategory з однаковою
-- назвою проходили клієнтську перевірку (findByNameIgnoreCaseAndOwner)
-- і обидва вставлялись у БД.
--
-- Тепер другий INSERT впаде з DataIntegrityViolationException, яку
-- CategoryService перетворює у ValidationException("category.name.exists").
--
-- ⚠️ Cleanup існуючих дублікатів перед створенням індексу.
-- Якщо в БД уже є (user_id, name) з однаковою назвою — залишаємо
-- найстаріший (min id). Якщо дублікат використовується в expense/income,
-- міграція впаде з FK-violation — це сигнал розібратись руками.
--
-- ⚠️ Case-insensitive унікальність (LOWER(name)) H2 2.x не підтримує
-- у CREATE UNIQUE INDEX. Для PostgreSQL у продакшн-конфізі додати
-- окремо:
--   CREATE UNIQUE INDEX uk_category_user_name_lower
--     ON category (user_id, LOWER(name));
-- =====================================================================


-- ==================== CLEANUP ====================
-- Видаляємо всі категорії, які мають "старшого брата" з такою самою
-- назвою в того самого юзера.
DELETE FROM category
WHERE id NOT IN (
    SELECT MIN(id)
    FROM category
    GROUP BY user_id, name
);


-- ==================== UNIQUE INDEX ====================
CREATE UNIQUE INDEX IF NOT EXISTS uk_category_user_name
    ON category (user_id, name);