package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Доповнює {@code CategoryServiceTest}: race condition (DataIntegrityViolation),
 * чужі/неіснуючі категорії при оновленні, trim/uppercase, bulk-видалення,
 * підрахунок використань.
 */
class CategoryServiceGapsTest {

    private CategoryRepository categoryRepository;
    private ExpenseRepository expenseRepository;
    private IncomeRepository incomeRepository;
    private CategoryService service;

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

        categoryRepository = mock(CategoryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        incomeRepository = mock(IncomeRepository.class);
        service = new CategoryService(categoryRepository, expenseRepository, incomeRepository, messageSource);

        user = new User();
        user.setId(1L);
        user.setUsername("owner");

        category = new Category();
        category.setId(10L);
        category.setName("Їжа");
        category.setType("EXPENSE");
        category.setOwner(user);

        when(categoryRepository.findByNameIgnoreCaseAndOwner(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== addCategory ====================

    @Test
    void add_raceCondition_isConvertedToValidationException() {
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("uk_category_user_name"));

        assertThatThrownBy(() -> service.addCategory("Їжа", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    @Test
    void add_trimsName_andUppercasesType() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category saved = service.addCategory("  Кава  ", "income", user);

        assertThat(saved.getName()).isEqualTo("Кава");
        assertThat(saved.getType()).isEqualTo("INCOME");
        verify(categoryRepository).findByNameIgnoreCaseAndOwner("Кава", user);
    }

    @Test
    void add_emptyName_neverTouchesRepository() {
        assertThatThrownBy(() -> service.addCategory("", "EXPENSE", user))
                .isInstanceOf(ValidationException.class);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    // ==================== updateCategory ====================

    @Test
    void update_notFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCategory(99L, "X", user))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_foreignCategory_throwsAccessDenied_andDoesNotSave() {
        User intruder = new User();
        intruder.setId(2L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.updateCategory(10L, "Hacked", intruder))
                .isInstanceOf(AccessDeniedException.class);

        verify(categoryRepository, never()).save(any(Category.class));
        assertThat(category.getName()).isEqualTo("Їжа");
    }

    @Test
    void update_raceCondition_isConvertedToValidationException() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("uk_category_user_name_lower"));

        assertThatThrownBy(() -> service.updateCategory(10L, "Нова", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    @Test
    void update_sameNameDifferentCase_onSameCategory_isAllowed() {
        // findByNameIgnoreCase знаходить саму категорію — це не дублікат
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByNameIgnoreCaseAndOwner("ЇЖА", user)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = service.updateCategory(10L, "ЇЖА", user);

        assertThat(result.getName()).isEqualTo("ЇЖА");
    }

    @Test
    void update_trimsName() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = service.updateCategory(10L, "  Обіди  ", user);

        assertThat(result.getName()).isEqualTo("Обіди");
    }

    // ==================== deleteCategory ====================

    @Test
    void delete_notFound_throwsEntityNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCategory(99L, user))
                .isInstanceOf(EntityNotFoundException.class);

        verify(categoryRepository, never()).deleteById(anyLong());
    }

    // ==================== deleteAllCategories ====================

    @Test
    void deleteAll_empty_doesNothing() {
        when(categoryRepository.findAllByOwner(user)).thenReturn(List.of());

        service.deleteAllCategories(user);

        verify(categoryRepository, never()).deleteAll(anyList());
        verify(categoryRepository, never()).flush();
    }

    @Test
    void deleteAll_nonEmpty_deletesThenFlushes() {
        List<Category> all = List.of(category);
        when(categoryRepository.findAllByOwner(user)).thenReturn(all);

        service.deleteAllCategories(user);

        InOrder inOrder = inOrder(categoryRepository);
        inOrder.verify(categoryRepository).deleteAll(all);
        inOrder.verify(categoryRepository).flush();
    }

    // ==================== читання / лічильники ====================

    @Test
    void countUsages_sumsExpensesAndIncomes() {
        when(expenseRepository.countByCategoryAndOwner(category, user)).thenReturn(4L);
        when(incomeRepository.countByCategoryAndOwner(category, user)).thenReturn(6L);

        assertThat(service.countUsages(category, user)).isEqualTo(10L);
    }

    @Test
    void getExpenseAndIncomeCategories_queryByUppercaseType() {
        when(categoryRepository.findByTypeAndOwner("EXPENSE", user)).thenReturn(List.of(category));
        when(categoryRepository.findByTypeAndOwner("INCOME", user)).thenReturn(List.of());

        assertThat(service.getExpenseCategories(user)).containsExactly(category);
        assertThat(service.getIncomeCategories(user)).isEmpty();
    }

    @Test
    void getAllCategories_andCountByOwner_delegate() {
        when(categoryRepository.findAllByOwner(user)).thenReturn(List.of(category));
        when(categoryRepository.countByOwner(user)).thenReturn(1L);

        assertThat(service.getAllCategories(user)).containsExactly(category);
        assertThat(service.countByOwner(user)).isEqualTo(1L);
    }

    @Test
    void getCategoryByIdAndType_foreignOwner_failsBeforeTypeCheck() {
        User intruder = new User();
        intruder.setId(2L);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.getCategoryByIdAndType(10L, intruder, "EXPENSE"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void add_savesCategoryOwnedByUser() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);

        service.addCategory("Спорт", "EXPENSE", user);

        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(user);
    }
}