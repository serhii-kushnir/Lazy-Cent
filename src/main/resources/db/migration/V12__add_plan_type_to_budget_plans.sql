ALTER TABLE budget_plans ADD COLUMN plan_type VARCHAR(20) NOT NULL DEFAULT 'EXPENSE';
ALTER TABLE budget_plans DROP CONSTRAINT IF EXISTS uk_budget_plans;
ALTER TABLE budget_plans ADD CONSTRAINT uk_budget_plans_type
    UNIQUE (user_id, budget_month, budget_year, plan_type);