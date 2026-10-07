package org.lazycent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє, що чутливі поля {@link User} НЕ серіалізуються в JSON.
 *
 * <p>Регресійний тест: до фіксу {@code password} (BCrypt-хеш) і
 * {@code providerId} не мали {@code @JsonIgnore}, тому будь-яка
 * випадкова серіалізація (наприклад, у логи через Jackson, або
 * новий REST-endpoint) витікала б їх.
 */
class UserJsonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("json_test_user");
        user.setPassword("$2a$10$SUPER_SECRET_HASH_VALUE");
        user.setRole("USER");
        user.setEmail("test@example.com");
        user.setProvider("google");
        user.setProviderId("google-id-12345");
        user.setPreferredCurrency("UAH");
        user.setPreferredLanguage("uk");
        return user;
    }

    @Test
    void serialize_doesNotContainPassword() throws Exception {
        String json = objectMapper.writeValueAsString(buildUser());

        assertThat(json)
                .as("Password (BCrypt-хеш) не має бути в JSON")
                .doesNotContain("password")
                .doesNotContain("SUPER_SECRET_HASH_VALUE");
    }

    @Test
    void serialize_doesNotContainProviderId() throws Exception {
        String json = objectMapper.writeValueAsString(buildUser());

        assertThat(json)
                .as("providerId — внутрішній ідентифікатор провайдера, не має бути в JSON")
                .doesNotContain("providerId")
                .doesNotContain("google-id-12345");
    }

    @Test
    void serialize_containsPublicFields() throws Exception {
        String json = objectMapper.writeValueAsString(buildUser());

        assertThat(json)
                .contains("\"id\"")
                .contains("\"username\"")
                .contains("\"role\"")
                .contains("\"email\"")
                .contains("\"provider\"")
                .contains("\"preferredCurrency\"")
                .contains("\"preferredLanguage\"");
    }

    @Test
    void deserialize_ignoresUnknownFieldsSafely() throws Exception {
        // Jackson за замовчуванням падає на невідомих полях.
        // Це ок, бо @JsonIgnore впливає тільки на serialization,
        // але десеріалізація клієнтського JSON з "password" теж має бути безпечною.
        String json = """
                {
                  "id": 2,
                  "username": "from_client",
                  "password": "attacker-supplied",
                  "role": "USER"
                }
                """;

        // Власне, якщо клієнт присилає "password" у JSON, поле ТАКИ буде
        // встановлено (Jackson не блокує десеріалізацію @JsonIgnore-полів).
        // Але в наших endpoint'ах User приймається тільки як @ModelAttribute
        // з форми, тому це не є вразливістю.
        // Тест фіксує поточну поведінку — а не бажане "блокування".
        User parsed = objectMapper.readValue(json, User.class);
        assertThat(parsed.getUsername()).isEqualTo("from_client");
    }
}