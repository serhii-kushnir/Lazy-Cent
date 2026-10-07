package org.lazycent.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(
        name = "category",
        uniqueConstraints = {
                // V5: case-sensitive на (user_id, name) — дешевший на точний match
                @UniqueConstraint(
                        name = "uk_category_user_name",
                        columnNames = {"user_id", "name"}
                ),
                // V6: case-insensitive на (user_id, name_lower) — захист від
                // race condition, коли два паралельні addCategory з різним
                // регістром проходять сервісну перевірку findByNameIgnoreCase.
                //
                // Дублюється в entity, щоб Hibernate ddl-auto створював
                // той самий index у тестах (Flyway у них вимкнено). Без
                // цього schema з тестів і з прода різниться, і
                // CategoryServiceUniqueConstraintTest не може перевірити
                // БД-рівень.
                @UniqueConstraint(
                        name = "uk_category_user_name_lower",
                        columnNames = {"user_id", "name_lower"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @NotBlank(message = "Назва категорії не може бути порожньою")
    @Size(max = 100, message = "Назва категорії не може перевищувати 100 символів")
    @Column(nullable = false)
    private String name;

    /**
     * Нормалізована назва в НИЖНЬОМУ регістрі — службове поле для
     * case-insensitive унікальності.
     *
     * <p><b>Навіщо:</b> H2 2.x не підтримує expression indexes
     * ({@code CREATE UNIQUE INDEX ON category (user_id, LOWER(name))}),
     * тому тримаємо окрему колонку, яка завжди містить
     * {@code name.toLowerCase()}.
     *
     * <p>Заповнюється автоматично у {@link #syncNameLower()} перед
     * кожним {@code persist}/{@code update}. Бізнес-логіка НЕ має
     * використовувати це поле — воно для БД.
     */
    @Column(name = "name_lower", nullable = false)
    private String nameLower;

    @NotBlank(message = "Тип категорії обов'язковий")
    @Pattern(regexp = "EXPENSE|INCOME", message = "Тип категорії може бути лише EXPENSE або INCOME")
    @Column(nullable = false)
    private String type;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    /**
     * Синхронізує {@link #nameLower} з {@link #name} перед
     * INSERT/UPDATE.
     */
    @PrePersist
    @PreUpdate
    protected void syncNameLower() {
        if (name != null) {
            nameLower = name.toLowerCase();
        }
    }
}