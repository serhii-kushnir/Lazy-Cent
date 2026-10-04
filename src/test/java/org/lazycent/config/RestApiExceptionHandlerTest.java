package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// ⚠️ ЯВНИЙ імпорт — щоб IDE не підставила java.nio.file.AccessDeniedException
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("rest_test_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    @Test
    void ajaxEndpoint_returns200Json_whenOk() throws Exception {
        when(categoryService.getExpenseCategories(any())).thenReturn(List.of());

        mockMvc.perform(get("/categories/expense").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void ajaxEndpoint_returns500Json_whenServiceThrows() throws Exception {
        when(categoryService.getExpenseCategories(any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/categories/expense").with(user(testUser)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.path").value("/categories/expense"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void ajaxEndpoint_returns403Json_whenAccessDenied() throws Exception {
        // org.springframework.security.access.AccessDeniedException extends RuntimeException
        // — підходить для thenThrow без throws-декларації.
        when(categoryService.getIncomeCategories(any()))
                .thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(get("/categories/income").with(user(testUser)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/categories/income"));
    }

    @Test
    void formEndpoint_stillRedirects_whenError() throws Exception {
        when(categoryService.addCategory(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/categories/add")
                        .with(user(testUser))
                        .with(csrf())
                        .param("name", "Test")
                        .param("type", "EXPENSE"))
                .andExpect(status().is3xxRedirection());
    }
}