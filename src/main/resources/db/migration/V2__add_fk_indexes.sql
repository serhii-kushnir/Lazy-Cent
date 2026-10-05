-- =====================================================================
-- V2 — індекси на foreign key колонки
-- =====================================================================
-- Без цих індексів H2 та PostgreSQL роблять full scan дочірньої
-- таблиці при:
--   1. перевірці FK (наприклад, DELETE category → перевірка expense.category_id)
--   2. JOIN'ах (expense → day_budget, expense → category)
--   3. фільтрації за user_id (day_budget, notes тощо)
--
-- MySQL/MariaDB створюють індекси на FK автоматично — H2 і PostgreSQL НІ.
-- =====================================================================


-- ==================== category ====================
-- FK на users. Потрібно для findByTypeAndOwner, findAllByOwner тощо.
CREATE INDEX IF NOT EXISTS idx_category_user ON category (user_id);


-- ==================== day_budget ====================
-- FK на users. Unique (date, user_id) не покриває запити, де фільтр
-- тільки по user_id (findAllByOwnerOrderByDateAsc, countByOwner).
CREATE INDEX IF NOT EXISTS idx_day_budget_user ON day_budget (user_id);

-- Композитний (user_id, date) — для findByOwnerAndDateAfterOrderByDateAsc
-- (каскадний перерахунок балансів). Unique (date, user_id) має інший
-- порядок колонок і H2/PG не завжди використовують його як prefix.
CREATE INDEX IF NOT EXISTS idx_day_budget_user_date ON day_budget (user_id, date);


-- ==================== expense ====================
-- FK на day_budget. Використовується у КОЖНОМУ запиті через Expense.
CREATE INDEX IF NOT EXISTS idx_expense_day_budget ON expense (day_budget_id);

-- FK на category. Потрібно для countByCategoryAndOwner (pre-check перед
-- видаленням категорії) і для DELETE категорії (перевірка FK).
CREATE INDEX IF NOT EXISTS idx_expense_category ON expense (category_id);


-- ==================== income ====================
-- Аналогічно expense.
CREATE INDEX IF NOT EXISTS idx_income_day_budget ON income (day_budget_id);
CREATE INDEX IF NOT EXISTS idx_income_category ON income (category_id);


-- ==================== budget_plans ====================
-- FK на users.
CREATE INDEX IF NOT EXISTS idx_budget_plans_user ON budget_plans (user_id);


-- ==================== category_budget_limits ====================
-- FK на category і users.
CREATE INDEX IF NOT EXISTS idx_cbl_category ON category_budget_limits (category_id);
CREATE INDEX IF NOT EXISTS idx_cbl_user ON category_budget_limits (user_id);


-- ==================== notes ====================
-- FK на users уже покрито композитним idx_notes_user_archived (user_id, archived).
-- Окремий індекс на user_id не потрібен: (user_id, archived) працює як
-- prefix для запитів, де фільтр тільки по user_id.


-- ==================== password_reset_token ====================
-- FK на users уже покрито idx_prt_user (user_id) з V1.
-- Unique на token — окремо.