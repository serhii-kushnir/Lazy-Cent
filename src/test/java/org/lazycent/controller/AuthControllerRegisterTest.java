package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Покриває {@code POST /register}.
 *
 * <p><b>Rate limit:</b> {@code RateLimitFilter} пускає лише 5 POST /register
 * з одного IP за вікно, а Spring-контекст (і фільтр) спільний для всіх тестів
 * класу. Тому кожен запит отримує унікальний {@code remoteAddr}.
 *
 * <p><b>Параметри:</b> {@code MockHttpServletRequestBuilder.param()} ДОДАЄ
 * значення, а не замінює. Тому форма збирається з мапи з перекриттям.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerRegisterTest {

    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    @Autowired private MockMvc mockMvc;

    @MockBean private UserService userService;

    @BeforeEach
    void setUp() {
        when(userService.findByUsername(anyString())).thenReturn(null);
        when(userService.findByEmail(anyString())).thenReturn(null);
    }

    /** Валідна форма; {@code overrides} замінюють значення, {@code null} прибирає параметр. */
    private MockHttpServletRequestBuilder registration(Map<String, String> overrides) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("username", "new_user");
        params.put("email", "new@example.com");
        params.put("password", "Password1!");
        params.put("passwordConfirm", "Password1!");
        params.put("privacyConsent", "true");
        params.put("termsConsent", "true");
        params.putAll(overrides);

        int n = IP_COUNTER.incrementAndGet();
        String ip = "10.20." + (n / 250) + "." + (n % 250 + 1);

        MockHttpServletRequestBuilder builder = post("/register")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
        params.forEach((k, v) -> {
            if (v != null) builder.param(k, v);
        });
        return builder;
    }

    private MockHttpServletRequestBuilder registration() {
        return registration(Map.of());
    }

    private static Map<String, String> with(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void register_success_redirectsToLogin_andCallsService() throws Exception {
        mockMvc.perform(registration())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).registerUser("new_user", "Password1!", "new@example.com");
    }

    @Test
    void register_passwordMismatch_staysOnForm_withFieldError() throws Exception {
        mockMvc.perform(registration(with("passwordConfirm", "Different1!")))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("fieldErrors", hasKey("passwordConfirm")));

        verify(userService, never()).registerUser(anyString(), anyString(), any());
    }

    @Test
    void register_usernameTaken_staysOnForm_withFieldError() throws Exception {
        when(userService.findByUsername("new_user")).thenReturn(new User());

        mockMvc.perform(registration())
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("fieldErrors", hasKey("username")));

        verify(userService, never()).registerUser(anyString(), anyString(), any());
    }

    @Test
    void register_emailTaken_staysOnForm_withFieldError() throws Exception {
        when(userService.findByEmail("new@example.com")).thenReturn(new User());

        mockMvc.perform(registration())
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("fieldErrors", hasKey("email")));

        verify(userService, never()).registerUser(anyString(), anyString(), any());
    }

    @Test
    void register_weakPassword_failsBeanValidation() throws Exception {
        mockMvc.perform(registration(with("password", "weak", "passwordConfirm", "weak")))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("fieldErrors", hasKey("password")));

        verify(userService, never()).registerUser(anyString(), anyString(), any());
    }

    @Test
    void register_withoutConsents_failsBeanValidation() throws Exception {
        Map<String, String> noConsents = new LinkedHashMap<>();
        noConsents.put("privacyConsent", null);
        noConsents.put("termsConsent", null);

        mockMvc.perform(registration(noConsents))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("fieldErrors", hasKey("privacyConsent")))
                .andExpect(model().attribute("fieldErrors", hasKey("termsConsent")));

        verify(userService, never()).registerUser(anyString(), anyString(), any());
    }

    @Test
    void register_clearsPasswordsInFormOnError() throws Exception {
        mockMvc.perform(registration(with("passwordConfirm", "Different1!")))
                .andExpect(model().attribute("registrationForm", hasProperty("password", nullValue())))
                .andExpect(model().attribute("registrationForm", hasProperty("passwordConfirm", nullValue())));
    }

    @Test
    void register_serviceThrows_redirectsBackToRegister_withErrorFlash() throws Exception {
        doThrow(new ValidationException("Імʼя вже існує"))
                .when(userService).registerUser(anyString(), anyString(), any());

        mockMvc.perform(registration())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("errorMessage", "Імʼя вже існує"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void register_withoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/register")
                        .with(request -> {
                            request.setRemoteAddr("10.99.0." + (IP_COUNTER.incrementAndGet() % 250 + 1));
                            return request;
                        })
                        .param("username", "new_user")
                        .param("password", "Password1!"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }
}