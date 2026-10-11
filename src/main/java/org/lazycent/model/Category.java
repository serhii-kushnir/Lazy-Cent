package org.lazycent.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
@Table(
        name = "category",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_category_user_name",
                        columnNames = {"user_id", "name"}
                ),
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

    @Column(name = "name_lower", nullable = false)
    private String nameLower;

    @NotBlank(message = "Тип категорії обов'язковий")
    @Pattern(regexp = "EXPENSE|INCOME", message = "Тип категорії може бути лише EXPENSE або INCOME")
    @Column(nullable = false)
    private String type;

    /**
     * P2.c: {@code owner} тепер LAZY.
     *
     * <p><b>Де читається:</b>
     * <ul>
     *   <li>{@code CategoryService.getCategoryById/deleteCategory/updateCategory} — всередині {@code @Transactional};</li>
     *   <li>Thymeleaf — {@code owner} НЕ використовується (шаблони читають тільки {@code name}, {@code id}, {@code type}).</li>
     * </ul>
     *
     * <p><b>Ризик LazyInit:</b> відсутній. Усі місця, де потрібен {@code owner}, або в транзакції, або в WHERE-фільтрі репозиторію.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @PrePersist
    @PreUpdate
    protected void syncNameLower() {
        if (name != null) {
            nameLower = name.toLowerCase();
        }
    }
}