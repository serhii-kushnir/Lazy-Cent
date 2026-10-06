package org.lazycent.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

@Entity
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

    /**
     * Optimistic lock version.
     *
     * <p>Захищає від «lost update», коли два запити одночасно редагують
     * ту саму категорію (наприклад, дві вкладки браузера). Hibernate
     * додає {@code WHERE version = ?} до UPDATE — якщо хтось між SELECT
     * і UPDATE змінив рядок, оновлення впаде з
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @NotBlank(message = "Назва категорії не може бути порожньою")
    @Size(max = 100, message = "Назва категорії не може перевищувати 100 символів")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Тип категорії обов'язковий")
    @Pattern(regexp = "EXPENSE|INCOME", message = "Тип категорії може бути лише EXPENSE або INCOME")
    @Column(nullable = false)
    private String type;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;
}