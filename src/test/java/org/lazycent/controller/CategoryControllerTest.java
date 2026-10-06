package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private CategoryService categoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("cat_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    // ==================== POST /categories/add ====================

    @Test
    void addCategory_redirectsToReferer_withSuccess() throws Exception {
        mockMvc.perform(post("/categories/add")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("name", "Їжа")
                        .param("type", "EXPENSE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(categoryService).addCategory("Їжа", "EXPENSE", testUser);
    }

    @Test
    void addCategory_showsErrorFlash_whenDuplicate() throws Exception {
        doThrow(new ValidationException("Категорія вже існує"))
                .when(categoryService).addCategory(anyString(), anyString(), any());

        mockMvc.perform(post("/categories/add")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("name", "Їжа")
                        .param("type", "EXPENSE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void addCategory_redirectsToRoot_whenNoReferer() throws Exception {
        mockMvc.perform(post("/categories/add")
                        .with(csrf())
                        .with(user(testUser))
                        .param("name", "Їжа")
                        .param("type", "EXPENSE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void addCategory_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/categories/add")
                        .with(csrf())
                        .param("name", "Їжа")
                        .param("type", "EXPENSE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ==================== POST /categories/delete/{id} ====================

    @Test
    void deleteCategory_redirectsToReferer_withSuccess() throws Exception {
        mockMvc.perform(post("/categories/delete/10")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(categoryService).deleteCategory(10L, testUser);
    }

    @Test
    void deleteCategory_showsErrorFlash_whenInUse() throws Exception {
        doThrow(new ValidationException("Категорія використовується"))
                .when(categoryService).deleteCategory(anyLong(), any());

        mockMvc.perform(post("/categories/delete/10")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // ==================== POST /categories/update/{id} ====================

    @Test
    void updateCategory_redirectsToReferer_withSuccess() throws Exception {
        mockMvc.perform(post("/categories/update/10")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("name", "Нова назва"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(categoryService).updateCategory(10L, "Нова назва", testUser);
    }

    @Test
    void updateCategory_showsErrorFlash_whenDuplicate() throws Exception {
        doThrow(new ValidationException("Ім'я зайняте"))
                .when(categoryService).updateCategory(anyLong(), anyString(), any());

        mockMvc.perform(post("/categories/update/10")
                        .with(csrf())
                        .with(user(testUser))
                        .header("Referer", "/home")
                        .param("name", "Зайнята"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }
}