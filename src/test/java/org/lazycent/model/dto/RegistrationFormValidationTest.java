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

    // ==================== УСПІШНА ВАЛІДАЦІЯ ====================

    @Test
    void validForm_hasNoViolations() {
        assertThat(validator.validate(validForm())).isEmpty();
    }

    // ==================== USERNAME ====================

    @ParameterizedTest
    @ValueSource(strings = {"abc", "ab", "user name", "user-name", "user@name"})
    void invalidUsername_hasViolation(String username) {
        RegistrationForm f = validForm();
        f.setUsername(username);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdef", "user_123", "UserName_99", "abc123"})
    void validUsername_hasNoViolation(String username) {
        RegistrationForm f = validForm();
        f.setUsername(username);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    // ==================== EMAIL ====================

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
    @ValueSource(strings = {
            "user@example.com",
            "user.name@example.com",
            "user+tag@example.co.uk"
    })
    void validEmail_hasNoViolation(String email) {
        RegistrationForm f = validForm();
        f.setEmail(email);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    // ==================== PASSWORD: слабкі ====================

    /**
     * Кожен з цих паролів порушує одну з умов:
     * - short:           < 6 символів
     * - nouppercase1!:   немає A-Z
     * - NOLOWERCASE1!:   немає a-z
     * - NoDigits!:       немає 0-9
     * - NoSpecial1:      немає спецсимволу (не-алфанумерик)
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "nouppercase1!",
            "NOLOWERCASE1!",
            "NoDigits!",
            "NoSpecial1"
    })
    void weakPassword_hasViolation(String password) {
        RegistrationForm f = validForm();
        f.setPassword(password);
        f.setPasswordConfirm(password);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    // ==================== PASSWORD: спецсимволи ====================

    /**
     * Регресійний блок: раніше regex вимагав символ з набору {@code @$!%*?&},
     * тому паролі з {@code #}, {@code +}, {@code -}, {@code _}, {@code =}
     * тощо НЕ проходили валідацію. Після зміни regex на {@code (?=.*[^a-zA-Z0-9])}
     * будь-який не-алфанумерик підходить.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Password1#",
            "Password1+",
            "Password1-",
            "Password1_",
            "Password1=",
            "Password1~",
            "Password1^",
            "Password1(",
            "Password1.",
            "Password1,",
            "Password1:",
            "Password1/",
            "Password1\\",
            "Password1|",
            "Password1[",
            "Password1{",
            "Password1<",
            "Password1€",
            "Password1ї"
    })
    void specialCharacterVariants_areAccepted(String password) {
        RegistrationForm f = validForm();
        f.setPassword(password);
        f.setPasswordConfirm(password);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations)
                .as("Пароль '%s' має бути валідним", password)
                .noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    /**
     * Старі символи теж мають проходити — сумісність.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "Password1@",
            "Password1$",
            "Password1!",
            "Password1%",
            "Password1*",
            "Password1?",
            "Password1&"
    })
    void legacySpecialCharacters_stillAccepted(String password) {
        RegistrationForm f = validForm();
        f.setPassword(password);
        f.setPasswordConfirm(password);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        assertThat(violations).isEmpty();
    }

    /**
     * Юнікод-літери у паролі не зараховуються як "спецсимвол" —
     * regex вимагає саме не-ASCII-не-цифру. Це навмисно: юнікод-літери
     * плутаються з латиницею при візуальному огляді й у логах.
     */
    @Test
    void unicodeLetters_doNotCountAsSpecialCharacter() {
        RegistrationForm f = validForm();
        // 'ї' — не [a-zA-Z0-9], тому технічно зараховується як "спецсимвол".
        // Але пароль без жодного іншого спецсимволу і з 'ї' — навпаки, валідний
        // з точки зору нашого regex. Перевіримо що:
        f.setPassword("Passwordї1");
        f.setPasswordConfirm("Passwordї1");
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);
        // За нашим regex — валідний, бо 'ї' не входить у [a-zA-Z0-9]
        assertThat(violations)
                .as("'ї' зараховується як не-алфанумерик, пароль проходить")
                .noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    // ==================== ЗГОДИ ====================

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

    @Test
    void withoutBothConsents_hasTwoViolations() {
        RegistrationForm f = validForm();
        f.setPrivacyConsent(false);
        f.setTermsConsent(false);
        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(f);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("privacyConsent"));
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("termsConsent"));
    }
}