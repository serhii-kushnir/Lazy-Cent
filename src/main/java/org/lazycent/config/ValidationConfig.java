package org.lazycent.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Підключає Spring {@link MessageSource} до Bean Validation.
 *
 * <p><b>Навіщо:</b> за замовчуванням {@code Hibernate Validator}
 * резолвить {@code {key}} у {@code message} через
 * {@code ValidationMessages.properties} на classpath root.
 * Spring {@link MessageSource} (з {@code messages.properties},
 * {@code messages_en.properties}) при цьому <b>не використовується</b>
 * — і {@code LocaleContextHolder.getLocale()} ігнорується.
 *
 * <p>Явне налаштування {@link LocalValidatorFactoryBean#setValidationMessageSource}
 * робить так, що:
 * <ol>
 *   <li>{@code @NotBlank(message = "{validation.username.notblank}")}
 *       резолвиться через {@code messages_*.properties};</li>
 *   <li>мова повідомлення відповідає локалі поточного запиту.</li>
 * </ol>
 */
@Configuration
public class ValidationConfig {

    @Bean
    public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
}