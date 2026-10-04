package org.lazycent.model.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFormValidationTest {

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

    private RegistrationForm validForm() {
        RegistrationForm f = new RegistrationForm();
        f.setUsername("testuser");
        f.setEmail("test@example.com");
        f.setPassword("Password1!");
        f.setPasswordConfirm("Password1!");
        f.setPrivacyConsent(true);
        f.setTermsConsent(true);
        return f;
    }

    @Test
    void validForm_hasNoViolations() {
        assertThat(validator.validate(validForm())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "ab", "user name", "user-name", "user@name"})
    void invalidUsername_hasViolation(String username) {
        RegistrationForm f = validForm();
        f.setUsername(username);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    /**
     * Hibernate Validator 8 вважає валідними email-и типу "a@b" (є @, обидві частини непорожні).
     * Тому використовуємо лише ті, які @Email гарантовано відкидає.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "no-at-sign.com", "@example.com", "user@"})
    void invalidEmail_hasViolation(String email) {
        RegistrationForm f = validForm();
        f.setEmail(email);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "nouppercase1!", "NOLOWERCASE1!", "NoDigits!", "NoSpecial1"})
    void weakPassword_hasViolation(String password) {
        RegistrationForm f = validForm();
        f.setPassword(password);
        f.setPasswordConfirm(password);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void withoutPrivacyConsent_hasViolation() {
        RegistrationForm f = validForm();
        f.setPrivacyConsent(false);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("privacyConsent"));
    }

    @Test
    void withoutTermsConsent_hasViolation() {
        RegistrationForm f = validForm();
        f.setTermsConsent(false);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("termsConsent"));
    }
}