package org.lazycent.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TransactionRequest {

    @NotNull(message = "{validation.transaction.categoryId}")
    private Long categoryId;

    @NotBlank(message = "{validation.transaction.description.notblank}")
    @Size(max = 255, message = "{validation.transaction.description.max}")
    private String description;

    @NotNull(message = "{validation.transaction.amount.notnull}")
    @Positive(message = "{validation.transaction.amount.positive}")
    private Double amount;

    @Min(value = 1, message = "{validation.transaction.quantity.min}")
    private Integer quantity = 1;

    @Size(max = 500, message = "{validation.transaction.details.max}")
    private String details;

    private Boolean shareWithFamily = false;

    private LocalTime time;

    @Size(max = 100, message = "{validation.transaction.buyer.max}")
    private String buyer;

    @Size(max = 100, message = "{validation.transaction.seller.max}")
    private String seller;

    @NotNull(message = "{validation.transaction.date.notnull}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}