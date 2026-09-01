// Income.java
package org.lazycent.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_budget_id", nullable = false)
    private DayBudget dayBudget;

    @NotNull(message = "Категорія обов'язкова")
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @NotBlank(message = "Опис не може бути порожнім")
    @Size(max = 255, message = "Опис не може перевищувати 255 символів")
    @Column(nullable = false)
    private String description;

    @NotNull(message = "Сума обов'язкова")
    @Positive(message = "Сума має бути більшою за 0")
    @Column(nullable = false)
    private Double amount;

    @Min(value = 1, message = "Кількість має бути не менше 1")
    @Column(nullable = true)
    private Integer quantity;

    @Size(max = 500, message = "Деталі не можуть перевищувати 500 символів")
    @Column(length = 500, nullable = true)
    private String details;

    @Column(nullable = true)
    private Boolean shareWithFamily;

    @Column(nullable = true)
    private LocalTime time;

    @Size(max = 100, message = "Покупець не може перевищувати 100 символів")
    @Column(nullable = true)
    private String buyer;

    @Size(max = 100, message = "Продавець не може перевищувати 100 символів")
    @Column(nullable = true)
    private String seller;
}