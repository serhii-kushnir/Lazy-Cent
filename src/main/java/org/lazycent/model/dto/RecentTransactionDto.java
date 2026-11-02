package org.lazycent.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Одна транзакція для блоку «Останні транзакції» на /home.
 * Легка, без посилань на сутності, щоб не тягнути lazy-залежності у view.
 */
public record RecentTransactionDto(
        Long id,
        LocalDate date,
        LocalTime time,
        String description,
        String categoryName,
        Double amount,
        boolean expense
) {}