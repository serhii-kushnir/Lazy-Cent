package org.lazycent.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegistrationForm {

    @NotBlank(message = "Ім'я користувача не може бути порожнім")
    @Size(min = 6, max = 30, message = "Ім'я користувача має містити від 6 до 30 символів")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Дозволені лише латинські літери, цифри та підкреслення")
    private String username;

    @NotBlank(message = "Email не може бути порожнім")
    @Email(message = "Некоректна електронна адреса")
    private String email;

    @NotBlank(message = "Пароль не може бути порожнім")
    @Size(min = 6, max = 100, message = "Пароль має містити від 6 до 100 символів")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{6,}$",
        message = "Пароль має містити малу та велику літеру, цифру та спецсимвол"
    )
    private String password;

    @NotBlank(message = "Підтвердження пароля не може бути порожнім")
    private String passwordConfirm;

    private boolean privacyConsent;
    private boolean termsConsent;
}