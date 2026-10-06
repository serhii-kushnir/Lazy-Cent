package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String TEST_USERNAME = "security_test_user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByUsername(TEST_USERNAME)
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername(TEST_USERNAME);
                    u.setPassword("encoded-password");
                    u.setRole("USER");
                    u.setPreferredCurrency("UAH");
                    return userRepository.save(u);
                });
    }

    // ==================== ПУБЛІЧНІ URL ====================

    @Test
    void actuatorHealth_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void loginPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void privacyPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk());
    }

    @Test
    void cssStatic_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/css/test.css"))
                .andExpect(status().isNotFound());
    }

    // ==================== ЗАХИЩЕНІ URL ====================

    @Test
    void budgetPage_redirectsToLogin_whenAnonymous() throws Exception {
        mockMvc.perform(get("/budget"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void budgetPage_isAccessible_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/budget").with(user(testUser)))
                .andExpect(status().isOk());
    }

    // ==================== CSRF ====================

    @Test
    void postWithoutCsrf_isForbidden() throws Exception {
        mockMvc.perform(post("/settings/currency")
                        .param("currency", "USD")
                        .with(user(testUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithCsrf_isProcessed() throws Exception {
        mockMvc.perform(post("/settings/currency")
                        .param("currency", "USD")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection());
    }
}