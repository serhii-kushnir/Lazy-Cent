package org.lazycent.model.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TransactionRequest {

    @NotNull(message = "Категорія обов'язкова")
    private Long categoryId;

    @NotBlank(message = "Опис не може бути порожнім")
    @Size(max = 255, message = "Опис не може перевищувати 255 символів")
    private String description;

    @NotNull(message = "Сума обов'язкова")
    @Positive(message = "Сума має бути більшою за 0")
    private Double amount;

    @Min(value = 1, message = "Кількість має бути не менше 1")
    private Integer quantity = 1;

    // ✅ ДОДАНО ВАЛІДАЦІЮ ДЛЯ ДЕТАЛЕЙ
    @Size(max = 500, message = "Деталі не можуть перевищувати 500 символів")
    private String details;

    private Boolean shareWithFamily = false;

    private LocalTime time;

    @Size(max = 100, message = "Покупець не може перевищувати 100 символів")
    private String buyer;

    @Size(max = 100, message = "Продавець не може перевищувати 100 символів")
    private String seller;

    @NotNull(message = "Дата обов'язкова")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}