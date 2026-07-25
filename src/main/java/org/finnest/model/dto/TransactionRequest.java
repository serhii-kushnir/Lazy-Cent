package org.finnest.model.dto;

import lombok.Data;
import org.finnest.model.TransactionType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class TransactionRequest {
    private Long categoryId;
    private String description;
    private Double amount;
    private Integer quantity = 1;
    private String details;
    private Boolean shareWithFamily = false;
    private LocalTime time;
    private String buyer;
    private String seller;
    private TransactionType type;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;
}