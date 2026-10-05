package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє налаштування сесій на РЕАЛЬНОМУ Tomcat.
 *
 * <p><b>Чому не MockMvc:</b> {@code Set-Cookie} формує контейнер сервлетів
 * (Tomcat), а не DispatcherServlet. {@code MockMvc} його не емулює.
 *
 * <p><b>Чому cookie передаються вручну:</b> {@code TestRestTemplate} за
 * замовчуванням НЕ керує cookies між запитами. Якщо не передати сесійну
 * cookie з GET /login у POST /login — сервер вважатиме, що це нова сесія,
 * CSRF-токен не зійдеться → 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionManagementTest {

    private static final String SESSION_COOKIE_NAME = "LAZYCENT_SESSION";

    @LocalServerPort
    private int port;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("session_test");
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole("USER");
        user.setPreferredCurrency("UAH");
        userRepository.save(user);
    }

    // ==================== ТЕСТИ ====================

    @Test
    void login_setsSessionCookie_withSecurityAttributes() {
        // 1. GET /login — отримуємо HTML (з CSRF) і Set-Cookie (сесія)
        ResponseEntity<String> loginPage = restTemplate.getForEntity(
                "http://localhost:" + port + "/login", String.class);
        assertThat(loginPage.getStatusCode()).isEqualTo(HttpStatus.OK);

        String csrf = extractCsrfToken(loginPage.getBody());
        String sessionCookie = extractSessionCookieHeader(loginPage.getHeaders());
        assertThat(sessionCookie)
                .as("GET /login має встановити сесійну cookie")
                .isNotNull();

        // 2. POST /login — з cookie + CSRF
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "session_test");
        form.add("password", "Password1!");
        form.add("_csrf", csrf);

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/login",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Void.class);

        // 3. Успішний логін → 302 на /home
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/home");

        // 4. Перевіряємо атрибути нової сесійної cookie
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).as("Set-Cookie header missing").isNotNull().isNotEmpty();

        String newSessionCookie = setCookies.stream()
                .filter(c -> c.startsWith(SESSION_COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Cookie '" + SESSION_COOKIE_NAME + "' not found in " + setCookies));

        assertThat(newSessionCookie).contains("HttpOnly");
        assertThat(newSessionCookie).contains("SameSite=Lax");
        assertThat(newSessionCookie).contains("Path=/");
        // У тестах secure=false → Secure не має бути
        assertThat(newSessionCookie).doesNotContain("Secure");
    }

    @Test
    void login_migratesSessionId_whenPreviousSessionExists() {
        // 1. GET /login — отримуємо початкову сесію
        ResponseEntity<String> firstPage = restTemplate.getForEntity(
                "http://localhost:" + port + "/login", String.class);

        String beforeCookieHeader = extractSessionCookieHeader(firstPage.getHeaders());
        assertThat(beforeCookieHeader).isNotNull();
        String sessionIdBefore = extractSessionId(beforeCookieHeader);

        // 2. POST /login з цією ж cookie + CSRF
        String csrf = extractCsrfToken(firstPage.getBody());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, beforeCookieHeader);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "session_test");
        form.add("password", "Password1!");
        form.add("_csrf", csrf);

        ResponseEntity<Void> response = restTemplate.exchange(
                "http://localhost:" + port + "/login",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        // 3. Session Fixation: ID сесії має змінитись
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull().isNotEmpty();

        String afterCookieHeader = setCookies.stream()
                .filter(c -> c.startsWith(SESSION_COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
        String sessionIdAfter = extractSessionId(afterCookieHeader);

        assertThat(sessionIdAfter)
                .as("Session ID має змінитись після логіну (session fixation захист)")
                .isNotEqualTo(sessionIdBefore);
    }

    // ==================== ДОПОМІЖНІ ====================

    /**
     * Витягує значення CSRF-токена з HTML форми логіну.
     * Thymeleaf рендерить його як {@code <input type="hidden" name="_csrf" value="..."/>}.
     */
    private String extractCsrfToken(String html) {
        assertThat(html).as("HTML сторінки логіну порожній").isNotNull();

        int nameIdx = html.indexOf("name=\"_csrf\"");
        assertThat(nameIdx).as("_csrf поле не знайдено в HTML").isGreaterThan(-1);

        int valStart = html.indexOf("value=\"", nameIdx);
        assertThat(valStart).as("value атрибут для _csrf не знайдено").isGreaterThan(-1);

        int valEnd = html.indexOf('"', valStart + 7);
        return html.substring(valStart + 7, valEnd);
    }

    /**
     * Знаходить сесійну cookie в списку Set-Cookie і повертає її
     * у форматі {@code LAZYCENT_SESSION=...} (тільки name=value, без атрибутів) —
     * саме так, як її очікує заголовок {@code Cookie} у наступному запиті.
     */
    private String extractSessionCookieHeader(HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;

        return setCookies.stream()
                .filter(c -> c.startsWith(SESSION_COOKIE_NAME + "="))
                .findFirst()
                .map(c -> {
                    int semicolon = c.indexOf(';');
                    return semicolon > 0 ? c.substring(0, semicolon) : c;
                })
                .orElse(null);
    }

    private String extractSessionId(String cookieHeader) {
        // cookieHeader має формат "LAZYCENT_SESSION=<value>"
        int eq = cookieHeader.indexOf('=');
        return cookieHeader.substring(eq + 1);
    }
}