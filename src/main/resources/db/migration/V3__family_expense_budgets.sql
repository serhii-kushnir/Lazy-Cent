CREATE TABLE family_expense_budgets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id BIGINT NOT NULL,
    budget_month INTEGER NOT NULL,
    budget_year INTEGER NOT NULL,
    total_budget DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_family_expense_budgets_family FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    CONSTRAINT uk_family_expense_budgets_month UNIQUE (family_id, budget_month, budget_year)
);

CREATE TABLE family_expense_category_limits (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id BIGINT NOT NULL,
    budget_month INTEGER NOT NULL,
    budget_year INTEGER NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    limit_amount DOUBLE PRECISION NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_family_expense_category_limits_family FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    CONSTRAINT uk_family_expense_category_limits_month UNIQUE (family_id, budget_month, budget_year, category_name)
);

CREATE INDEX idx_family_expense_budgets_family ON family_expense_budgets (family_id);
CREATE INDEX idx_family_expense_category_limits_family ON family_expense_category_limits (family_id);
