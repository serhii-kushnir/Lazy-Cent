package org.lazycent.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistrationForm {

    @NotBlank(message = "{validation.username.notblank}")
    @Pattern(
            regexp = "^[a-zA-Z0-9_]{6,30}$",
            message = "{validation.username.pattern}"
    )
    private String username;

    @NotBlank(message = "{validation.email.notblank}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 100, message = "{validation.email.max}")
    private String email;

    @NotBlank(message = "{validation.password.notblank}")
    @Size(min = 6, max = 100, message = "{validation.password.size}")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
            message = "{validation.password.pattern}"
    )
    private String password;

    @NotBlank(message = "{validation.passwordConfirm.notblank}")
    private String passwordConfirm;

    @AssertTrue(message = "{validation.privacyConsent}")
    private boolean privacyConsent;

    @AssertTrue(message = "{validation.termsConsent}")
    private boolean termsConsent;
}