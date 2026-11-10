-- ==================== USERS ====================
CREATE TABLE users (
                       id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       username           VARCHAR(255) NOT NULL UNIQUE,
                       password           VARCHAR(255) NOT NULL,
                       role               VARCHAR(255) NOT NULL,
                       email              VARCHAR(255) UNIQUE,
                       provider           VARCHAR(255),
                       provider_id        VARCHAR(255),
                       preferred_currency VARCHAR(255) NOT NULL,
                       preferred_language VARCHAR(255),
                       notification_message VARCHAR(255)
);

-- ==================== FAMILY ====================
CREATE TABLE family (
                        id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        name        VARCHAR(100) NOT NULL UNIQUE,
                        invite_code VARCHAR(12)  NOT NULL UNIQUE,
                        owner_id    BIGINT       NOT NULL,
                        created_at  TIMESTAMP    NOT NULL,
                        CONSTRAINT fk_family_owner FOREIGN KEY (owner_id) REFERENCES users(id)
);

ALTER TABLE users ADD COLUMN family_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_family
    FOREIGN KEY (family_id) REFERENCES family(id);
CREATE INDEX idx_users_family ON users (family_id);

-- ==================== FAMILY JOIN REQUEST ====================
CREATE TABLE family_join_request (
                                     id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     family_id   BIGINT    NOT NULL,
                                     user_id     BIGINT    NOT NULL,
                                     created_at  TIMESTAMP NOT NULL,
                                     rejected    BOOLEAN   NOT NULL DEFAULT FALSE,
                                     CONSTRAINT fk_fjr_family FOREIGN KEY (family_id) REFERENCES family(id) ON DELETE CASCADE,
                                     CONSTRAINT fk_fjr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                     CONSTRAINT uk_fjr_family_user UNIQUE (family_id, user_id)
);

-- ==================== CATEGORY ====================
CREATE TABLE category (
                          id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          name       VARCHAR(255) NOT NULL,
                          name_lower VARCHAR(255) NOT NULL,
                          type       VARCHAR(255) NOT NULL,
                          version    BIGINT NOT NULL DEFAULT 0,
                          user_id    BIGINT NOT NULL,
                          CONSTRAINT fk_category_user FOREIGN KEY (user_id) REFERENCES users(id),
                          CONSTRAINT uk_category_user_name UNIQUE (user_id, name),
                          CONSTRAINT uk_category_user_name_lower UNIQUE (user_id, name_lower)
);
CREATE INDEX idx_category_user ON category (user_id);

-- ==================== DAY_BUDGET ====================
CREATE TABLE day_budget (
                            id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            date              DATE NOT NULL,
                            day_name          VARCHAR(255),
                            starting_balance  DOUBLE PRECISION,
                            remaining_balance DOUBLE PRECISION,
                            version           BIGINT NOT NULL DEFAULT 0,
                            user_id           BIGINT NOT NULL,
                            CONSTRAINT uk_day_budget_date_user UNIQUE (date, user_id),
                            CONSTRAINT fk_day_budget_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_day_budget_user ON day_budget (user_id);
CREATE INDEX idx_day_budget_user_date ON day_budget (user_id, date);

-- ==================== EXPENSE ====================
CREATE TABLE expense (
                         id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         day_budget_id     BIGINT NOT NULL,
                         category_id       BIGINT NOT NULL,
                         description       VARCHAR(255),
                         amount            DOUBLE PRECISION,
                         quantity          INTEGER,
                         details           VARCHAR(500),
                         share_with_family BOOLEAN,
                         time              TIME,
                         buyer             VARCHAR(255),
                         seller            VARCHAR(255),
                         CONSTRAINT fk_expense_day_budget FOREIGN KEY (day_budget_id) REFERENCES day_budget(id),
                         CONSTRAINT fk_expense_category   FOREIGN KEY (category_id)   REFERENCES category(id)
);
CREATE INDEX idx_expense_day_budget ON expense (day_budget_id);
CREATE INDEX idx_expense_category ON expense (category_id);

-- ==================== INCOME ====================
CREATE TABLE income (
                        id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        day_budget_id     BIGINT NOT NULL,
                        category_id       BIGINT NOT NULL,
                        description       VARCHAR(255),
                        amount            DOUBLE PRECISION,
                        quantity          INTEGER,
                        details           VARCHAR(500),
                        share_with_family BOOLEAN,
                        time              TIME,
                        buyer             VARCHAR(255),
                        seller            VARCHAR(255),
                        CONSTRAINT fk_income_day_budget FOREIGN KEY (day_budget_id) REFERENCES day_budget(id),
                        CONSTRAINT fk_income_category   FOREIGN KEY (category_id)   REFERENCES category(id)
);
CREATE INDEX idx_income_day_budget ON income (day_budget_id);
CREATE INDEX idx_income_category ON income (category_id);

-- ==================== BUDGET_PLANS ====================
CREATE TABLE budget_plans (
                              id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                              user_id      BIGINT NOT NULL,
                              budget_month INTEGER NOT NULL,
                              budget_year  INTEGER NOT NULL,
                              total_budget DOUBLE PRECISION NOT NULL,
                              plan_type    VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
                              created_at   TIMESTAMP NOT NULL,
                              updated_at   TIMESTAMP,
                              CONSTRAINT fk_budget_plans_user FOREIGN KEY (user_id) REFERENCES users(id),
                              CONSTRAINT uk_budget_plans_type UNIQUE (user_id, budget_month, budget_year, plan_type)
);
CREATE INDEX idx_budget_plans_user ON budget_plans (user_id);

-- ==================== CATEGORY_BUDGET_LIMITS ====================
CREATE TABLE category_budget_limits (
                                        id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                        category_id  BIGINT NOT NULL,
                                        user_id      BIGINT NOT NULL,
                                        budget_month INTEGER NOT NULL,
                                        budget_year  INTEGER NOT NULL,
                                        limit_amount DOUBLE PRECISION NOT NULL,
                                        note         VARCHAR(255),
                                        created_at   TIMESTAMP NOT NULL,
                                        updated_at   TIMESTAMP,
                                        CONSTRAINT uk_cbl UNIQUE (category_id, user_id, budget_month, budget_year),
                                        CONSTRAINT fk_cbl_category FOREIGN KEY (category_id) REFERENCES category(id),
                                        CONSTRAINT fk_cbl_user     FOREIGN KEY (user_id)     REFERENCES users(id)
);
CREATE INDEX idx_cbl_category ON category_budget_limits (category_id);
CREATE INDEX idx_cbl_user ON category_budget_limits (user_id);

-- ==================== NOTES ====================
CREATE TABLE notes (
                       id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       user_id           BIGINT NOT NULL,
                       title             VARCHAR(255) NOT NULL,
                       content           VARCHAR(5000),
                       color             VARCHAR(255),
                       text_color        VARCHAR(255) NOT NULL DEFAULT '#212529',
                       pinned            BOOLEAN NOT NULL DEFAULT FALSE,
                       archived          BOOLEAN NOT NULL DEFAULT FALSE,
                       share_with_family BOOLEAN NOT NULL DEFAULT FALSE,
                       version           BIGINT NOT NULL DEFAULT 0,
                       created_at        TIMESTAMP,
                       updated_at        TIMESTAMP,
                       CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_notes_user_archived ON notes (user_id, archived);
CREATE INDEX idx_notes_share_family ON notes (user_id, share_with_family);

-- ==================== PASSWORD_RESET_TOKEN ====================
CREATE TABLE password_reset_token (
                                      id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      user_id     BIGINT NOT NULL,
                                      token       VARCHAR(64) NOT NULL UNIQUE,
                                      expiry_date TIMESTAMP NOT NULL,
                                      created_at  TIMESTAMP NOT NULL,
                                      CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_prt_user ON password_reset_token (user_id);