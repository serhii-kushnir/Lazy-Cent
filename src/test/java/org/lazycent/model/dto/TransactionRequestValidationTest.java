package org.lazycent.model.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.close();
    }

    private TransactionRequest validRequest() {
        TransactionRequest r = new TransactionRequest();
        r.setCategoryId(1L);
        r.setDescription("Продукти");
        r.setAmount(100.0);
        r.setQuantity(1);
        r.setDate(LocalDate.now());
        return r;
    }

    @Test
    void validRequest_hasNoViolations() {
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(validRequest());
        assertThat(violations).isEmpty();
    }

    @Test
    void nullCategoryId_isInvalid() {
        TransactionRequest r = validRequest();
        r.setCategoryId(null);
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(r);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("categoryId"));
    }

    @Test
    void blankDescription_isInvalid() {
        TransactionRequest r = validRequest();
        r.setDescription("   ");
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(r);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("description"));
    }

    @Test
    void negativeAmount_isInvalid() {
        TransactionRequest r = validRequest();
        r.setAmount(-10.0);
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(r);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    void zeroAmount_isInvalid() {
        TransactionRequest r = validRequest();
        r.setAmount(0.0);
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(r);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    void descriptionTooLong_isInvalid() {
        TransactionRequest r = validRequest();
        r.setDescription("a".repeat(256));
        Set<ConstraintViolation<TransactionRequest>> violations = validator.validate(r);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("description"));
    }
}