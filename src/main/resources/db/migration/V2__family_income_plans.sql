CREATE TABLE family_income_plans (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id BIGINT NOT NULL,
    budget_month INTEGER NOT NULL,
    budget_year INTEGER NOT NULL,
    target_income DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_family_income_plans_family FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    CONSTRAINT uk_family_income_plans_month UNIQUE (family_id, budget_month, budget_year)
);

CREATE TABLE family_income_category_targets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id BIGINT NOT NULL,
    budget_month INTEGER NOT NULL,
    budget_year INTEGER NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    limit_amount DOUBLE PRECISION NOT NULL,
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_family_income_category_targets_family FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
    CONSTRAINT uk_family_income_category_targets_month UNIQUE (family_id, budget_month, budget_year, category_name)
);

CREATE INDEX idx_family_income_plans_family ON family_income_plans (family_id);
CREATE INDEX idx_family_income_category_targets_family ON family_income_category_targets (family_id);
