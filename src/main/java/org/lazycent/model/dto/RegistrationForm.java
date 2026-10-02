package org.lazycent.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для форми реєстрації.
 * Містить усі правила валідації декларативно — через анотації Bean Validation.
 */
@Data
public class RegistrationForm {

    @NotBlank(message = "Ім'я користувача не може бути порожнім.")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{6,30}$",
            message = "Ім'я користувача має містити 6-30 символів (літери, цифри, підкреслення)."
    )
    private String username;

    @NotBlank(message = "Електронна пошта не може бути порожньою.")
    @Email(message = "Введіть коректну електронну пошту.")
    @Size(max = 100, message = "Email не може перевищувати 100 символів.")
    private String email;

    @NotBlank(message = "Пароль не може бути порожнім.")
    @Size(min = 6, max = 100, message = "Пароль має містити щонайменше 6 символів.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
            message = "Пароль має містити малу та велику літеру, цифру і спецсимвол (@$!%*?&)."
    )
    private String password;

    @NotBlank(message = "Підтвердження пароля не може бути порожнім.")
    private String passwordConfirm;

    @AssertTrue(message = "Необхідно погодитися з Політикою конфіденційності.")
    private boolean privacyConsent;

    @AssertTrue(message = "Необхідно погодитися з Умовами використання.")
    private boolean termsConsent;
}