package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private IncomeRepository incomeRepository;

    private CategoryService categoryService;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        categoryService = new CategoryService(
                categoryRepository,
                expenseRepository,
                incomeRepository,
                messageSource);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        category = new Category();
        category.setId(10L);
        category.setName("Їжа");
        category.setType("EXPENSE");
        category.setOwner(user);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== addCategory ====================

    @Test
    void addCategory_savesNewCategory_whenValid() {
        when(categoryRepository.findByNameIgnoreCaseAndOwner("Їжа", user))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.addCategory("Їжа", "expense", user);

        assertThat(result.getName()).isEqualTo("Їжа");
        assertThat(result.getType()).isEqualTo("EXPENSE");
        assertThat(result.getOwner()).isEqualTo(user);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void addCategory_throwsValidation_whenNameIsEmpty() {
        assertThatThrownBy(() -> categoryService.addCategory("   ", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("порожньою");
    }

    @Test
    void addCategory_throwsValidation_whenDuplicate() {
        when(categoryRepository.findByNameIgnoreCaseAndOwner("Їжа", user))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.addCategory("Їжа", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    // ==================== getCategoryById ====================

    @Test
    void getCategoryById_returnsCategory_whenOwnerMatches() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryById(10L, user);

        assertThat(result).isEqualTo(category);
    }

    @Test
    void getCategoryById_throwsEntityNotFound_whenNotExists() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99L, user))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getCategoryById_throwsAccessDenied_whenDifferentOwner() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.getCategoryById(10L, otherUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("доступу");
    }

    // ==================== getCategoryByIdAndType ====================

    @Test
    void getCategoryByIdAndType_returnsCategory_whenTypeMatches() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryByIdAndType(10L, user, "EXPENSE");

        assertThat(result).isEqualTo(category);
    }

    @Test
    void getCategoryByIdAndType_isCaseInsensitive() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryByIdAndType(10L, user, "expense");

        assertThat(result).isEqualTo(category);
    }

    @Test
    void getCategoryByIdAndType_throwsValidation_whenTypeMismatch() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.getCategoryByIdAndType(10L, user, "INCOME"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("EXPENSE");
    }

    // ==================== deleteCategory ====================

    @Test
    void deleteCategory_deletesWhenOwnerMatches_andNoUsages() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(expenseRepository.countByCategoryAndOwner(category, user)).thenReturn(0L);
        when(incomeRepository.countByCategoryAndOwner(category, user)).thenReturn(0L);

        categoryService.deleteCategory(10L, user);

        verify(categoryRepository).deleteById(10L);
    }

    @Test
    void deleteCategory_throwsAccessDenied_forOtherOwner() {
        User otherUser = new User();
        otherUser.setId(2L);

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory(10L, otherUser))
                .isInstanceOf(AccessDeniedException.class);

        verify(categoryRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteCategory_throwsValidation_whenUsedInExpenses() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(expenseRepository.countByCategoryAndOwner(category, user)).thenReturn(5L);
        when(incomeRepository.countByCategoryAndOwner(category, user)).thenReturn(0L);

        assertThatThrownBy(() -> categoryService.deleteCategory(10L, user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("5");    // повідомлення містить кількість

        verify(categoryRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteCategory_throwsValidation_whenUsedInIncomes() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(expenseRepository.countByCategoryAndOwner(category, user)).thenReturn(0L);
        when(incomeRepository.countByCategoryAndOwner(category, user)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteCategory(10L, user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("3");

        verify(categoryRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteCategory_throwsValidation_whenUsedInBoth() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(expenseRepository.countByCategoryAndOwner(category, user)).thenReturn(2L);
        when(incomeRepository.countByCategoryAndOwner(category, user)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.deleteCategory(10L, user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("5");    // 2 + 3

        verify(categoryRepository, never()).deleteById(anyLong());
    }

    // ==================== updateCategory ====================

    @Test
    void updateCategory_updatesName_whenValid() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCaseAndOwner("Нова назва", user))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.updateCategory(10L, "Нова назва", user);

        assertThat(result.getName()).isEqualTo("Нова назва");
    }

    @Test
    void updateCategory_throwsValidation_whenNameEmpty() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.updateCategory(10L, "   ", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("порожньою");
    }

    @Test
    void updateCategory_throwsValidation_whenDuplicate() {
        Category existing = new Category();
        existing.setId(20L);
        existing.setName("Нова назва");
        existing.setOwner(user);

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCaseAndOwner("Нова назва", user))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.updateCategory(10L, "Нова назва", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }
}