package org.lazycent.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TransactionRequest {

    @NotNull(message = "{validation.category.required}")
    private Long categoryId;

    @NotBlank(message = "{validation.description.blank}")
    @Size(max = 255, message = "{validation.description.max}")
    private String description;

    @NotNull(message = "{validation.amount.required}")
    @Positive(message = "{validation.amount.positive}")
    private Double amount;

    @Min(value = 1, message = "{validation.quantity.min}")
    private Integer quantity = 1;

    @Size(max = 500, message = "{validation.details.max}")
    private String details;

    private Boolean shareWithFamily = false;

    private LocalTime time;

    @Size(max = 100, message = "{validation.buyer.max}")
    private String buyer;

    @Size(max = 100, message = "{validation.seller.max}")
    private String seller;

    @NotNull(message = "{validation.date.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}