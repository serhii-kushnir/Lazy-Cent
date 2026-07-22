package org.finnest.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Income {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "day_budget_id")
    private DayBudget dayBudget;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String description;
    private Double amount;

    @Column(nullable = true)
    private Integer quantity;

    @Column(length = 500, nullable = true)
    private String details;

    @Column(nullable = true)
    private Boolean shareWithFamily;

    @Column(nullable = true)
    private LocalTime time;

    @Column(nullable = true)
    private String buyer;

    @Column(nullable = true)
    private String seller;
}