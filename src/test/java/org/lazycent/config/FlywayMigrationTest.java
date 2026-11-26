package org.lazycent.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-тест, який перевіряє, що всі Flyway-міграції застосовуються
 * на чисту БД і що Hibernate {@code ddl-auto: validate} проходить.
 *
 * <p><b>Навіщо:</b> в {@code application-test.yaml} Flyway вимкнено
 * (щоб не сповільнювати unit-тести і щоб Hibernate сам створював схему
 * під кожен тест). Але це означає, що SQL-міграції ніколи не виконуються
 * в тестах — і синтаксична помилка в {@code V2}/{@code V3} виявиться
 * тільки в проді.
 */
@SpringBootTest
@ActiveProfiles("flyway-test")
class FlywayMigrationTest {

    @Autowired
    private DataSource dataSource;

    /**
     * Smoke-тест: контекст піднявся — значить Flyway застосував усі
     * міграції і Hibernate {@code validate} пройшов.
     */
    @Test
    void contextLoads_withAllMigrationsApplied() {
        assertThat(dataSource).isNotNull();
    }

    // ==================== FLYWAY HISTORY ====================

    /**
     * H2 2.x case-sensitive: Flyway створює таблицю як
     * {@code "flyway_schema_history"} (lowercase, quoted). Щоб
     * звернутись до неї, треба теж використовувати лапки або
     * INFORMATION_SCHEMA.
     */
    @Test
    void flywayHistory_containsAllMigrations_withSuccess() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             // Використовуємо SELECT *, а доступ до колонок — за іменами
             // через getString(...). Flyway 10 створив таблицю з lowercase-
             // quoted іменами, тому і таблиця, і колонки вимагають лапок
             // при зверненні через SQL. SELECT * цю проблему обходить.
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")) {

            Set<String> appliedVersions = new HashSet<>();
            int count = 0;

            while (rs.next()) {
                boolean success = rs.getBoolean("success");
                String version = rs.getString("version");
                String description = rs.getString("description");

                assertThat(success)
                        .as("Міграція V%s (%s) не застосована успішно", version, description)
                        .isTrue();

                if (version != null) {
                    appliedVersions.add(version);
                }
                count++;
            }

            assertThat(count).as("Кількість записів у flyway_schema_history").isGreaterThanOrEqualTo(3);
            assertThat(appliedVersions)
                    .as("Усі активні версії міграцій мають бути в історії")
                    .containsExactlyInAnyOrder("1", "2", "3");
        }
    }

    // ==================== CATEGORY VERSION ====================

    @Test
    void categoryTable_hasVersionColumn() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(
                     null, "PUBLIC", "CATEGORY", "VERSION")) {

            assertThat(rs.next())
                    .as("V3 мала додати колонку 'version' у 'category'")
                    .isTrue();

            String typeName = rs.getString("TYPE_NAME");
            assertThat(typeName).as("Тип колонки version").isEqualToIgnoringCase("BIGINT");
        }
    }

    @Test
    void categoryVersionColumn_isNotNull() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             // INFORMATION_SCHEMA без лапок — H2 нормалізує в UPPER
             ResultSet rs = stmt.executeQuery(
                     "SELECT is_nullable, column_default " +
                             "FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE table_name = 'CATEGORY' AND column_name = 'VERSION'")) {

            assertThat(rs.next()).as("Колонка version має існувати").isTrue();

            String isNullable = rs.getString("is_nullable");
            assertThat(isNullable)
                    .as("V3 мала створити NOT NULL колонку")
                    .isEqualToIgnoringCase("NO");
        }
    }

    // ==================== DATABASE INDEXES ====================

    @Test
    void fkIndexes_allExist() throws SQLException {
        // Імена індексів у V2 — без лапок, H2 нормалізує в UPPER.
        Set<String> expectedIndexes = Set.of(
                "IDX_CATEGORY_USER",
                "IDX_DAY_BUDGET_USER",
                "IDX_DAY_BUDGET_USER_DATE",
                "IDX_EXPENSE_DAY_BUDGET",
                "IDX_EXPENSE_CATEGORY",
                "IDX_INCOME_DAY_BUDGET",
                "IDX_INCOME_CATEGORY",
                "IDX_BUDGET_PLANS_USER",
                "IDX_CBL_CATEGORY",
                "IDX_CBL_USER"
        );

        Set<String> actualIndexes = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT index_name " +
                             "FROM INFORMATION_SCHEMA.INDEX_COLUMNS " +
                             "WHERE table_schema = 'PUBLIC'")) {

            while (rs.next()) {
                actualIndexes.add(rs.getString("index_name").toUpperCase());
            }
        }

        assertThat(actualIndexes)
                .as("Усі індекси з V2 мають бути створені")
                .containsAll(expectedIndexes);
    }

    // ==================== БАЗОВІ ТАБЛИЦІ ====================

    @Test
    void baseTables_exist() throws SQLException {
        Set<String> expectedTables = Set.of(
                "USERS", "CATEGORY", "DAY_BUDGET", "EXPENSE", "INCOME",
                "BUDGET_PLANS", "CATEGORY_BUDGET_LIMITS",
                "NOTES", "PASSWORD_RESET_TOKEN"
        );

        Set<String> actualTables = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(
                     null, "PUBLIC", "%", new String[]{"TABLE"})) {

            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME").toUpperCase());
            }
        }

        assertThat(actualTables)
                .as("Усі таблиці з V1 мають бути створені")
                .containsAll(expectedTables);
    }

    @Test
    void noteTextColor_hasDefaultValue() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT column_default " +
                             "FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE table_name = 'NOTES' AND column_name = 'TEXT_COLOR'")) {

            assertThat(rs.next()).as("Колонка text_color має існувати").isTrue();

            String defaultValue = rs.getString("column_default");
            assertThat(defaultValue)
                    .as("V1 мала встановити DEFAULT '#212529'")
                    .isNotNull()
                    .contains("#212529");
        }
    }

    @Test
    void categoryUniqueIndex_exists() throws SQLException {
        Set<String> uniqueConstraints = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT constraint_name " +
                             "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                             "WHERE table_schema = 'PUBLIC' " +
                             "  AND table_name = 'CATEGORY' " +
                             "  AND constraint_type = 'UNIQUE'")) {

            while (rs.next()) {
                uniqueConstraints.add(rs.getString("constraint_name").toUpperCase());
            }
        }

        assertThat(uniqueConstraints)
                .as("V1 мала створити unique constraint uk_category_user_name")
                .contains("UK_CATEGORY_USER_NAME");
    }

    // ==================== CASE-INSENSITIVE CATEGORY UNIQUENESS ====================

    @Test
    void categoryTable_hasNameLowerColumn() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(
                     null, "PUBLIC", "CATEGORY", "NAME_LOWER")) {

            assertThat(rs.next())
                    .as("V6 мала додати колонку 'name_lower' у 'category'")
                    .isTrue();

            String isNullable = rs.getString("IS_NULLABLE");
            assertThat(isNullable)
                    .as("V6 мала створити NOT NULL колонку")
                    .isEqualToIgnoringCase("NO");
        }
    }

    @Test
    void categoryCaseInsensitiveUniqueIndex_exists() throws SQLException {
        Set<String> uniqueConstraints = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT constraint_name " +
                             "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                             "WHERE table_schema = 'PUBLIC' " +
                             "  AND table_name = 'CATEGORY' " +
                             "  AND constraint_type = 'UNIQUE'")) {

            while (rs.next()) {
                uniqueConstraints.add(rs.getString("constraint_name").toUpperCase());
            }
        }

        assertThat(uniqueConstraints)
                .as("V1 мала створити unique constraint uk_category_user_name_lower")
                .contains("UK_CATEGORY_USER_NAME_LOWER");
    }
}