package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Category;
import org.lazycent.model.DayBudget;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.ExpenseRepository;
import org.lazycent.repository.IncomeRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DemoDataServiceTest {

    private CategoryRepository categoryRepository;
    private DayBudgetRepository dayBudgetRepository;
    private ExpenseRepository expenseRepository;
    private IncomeRepository incomeRepository;
    private DemoDataService service;

    private User user;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        categoryRepository = mock(CategoryRepository.class);
        dayBudgetRepository = mock(DayBudgetRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        incomeRepository = mock(IncomeRepository.class);

        service = new DemoDataService(
                categoryRepository,
                dayBudgetRepository,
                expenseRepository,
                incomeRepository,
                messageSource
        );

        user = new User();
        user.setId(1L);
        user.setUsername("demo_test_user");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== createDefaultCategories ====================

    @Test
    void createDefaultCategories_creates9Categories_whenNoneExist() {
        when(categoryRepository.countByOwner(user)).thenReturn(0L);
        when(categoryRepository.findByNameAndOwner(anyString(), eq(user)))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createDefaultCategories(user);

        // 5 EXPENSE + 4 INCOME = 9
        verify(categoryRepository, times(9)).save(any(Category.class));
    }

    @Test
    void createDefaultCategories_skips_whenCategoriesExist() {
        when(categoryRepository.countByOwner(user)).thenReturn(9L);

        service.createDefaultCategories(user);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void createDefaultCategories_usesLocalizedNames() {
        when(categoryRepository.countByOwner(user)).thenReturn(0L);
        when(categoryRepository.findByNameAndOwner(anyString(), eq(user)))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createDefaultCategories(user);

        // Перевіряємо, що серед створених є "Їжа" і "Зарплата"
        // (з messages.properties, локалізація uk-UA)
        verify(categoryRepository).save(argThat(c ->
                "Їжа".equals(c.getName()) && "EXPENSE".equals(c.getType())));
        verify(categoryRepository).save(argThat(c ->
                "Зарплата".equals(c.getName()) && "INCOME".equals(c.getType())));
    }

    // ==================== createRandomTransactions ====================

    @Test
    void createRandomTransactions_createsCategoriesFirst_whenNoneExist() {
        when(categoryRepository.countByOwner(user)).thenReturn(0L, 9L);
        when(dayBudgetRepository.countByOwner(user)).thenReturn(0L);

        doAnswer(inv -> {
            String name = inv.getArgument(0);
            Category c = new Category();
            c.setId((long) Math.abs(name.hashCode()));
            c.setName(name);
            c.setType("EXPENSE");
            c.setOwner(user);
            return Optional.of(c);
        }).when(categoryRepository).findByNameAndOwner(anyString(), eq(user));

        when(dayBudgetRepository.findByDateAndOwner(any(), eq(user)))
                .thenReturn(Optional.empty());
        when(dayBudgetRepository.save(any(DayBudget.class)))
                .thenAnswer(inv -> {
                    DayBudget b = inv.getArgument(0);
                    if (b.getId() == null) b.setId(100L);
                    return b;
                });

        service.createRandomTransactions(user, 5);

        // 5 днів, кожен зберігається ДВІЧІ:
        //   1) при створенні (orElseGet)
        //   2) при оновленні remainingBalance
        verify(dayBudgetRepository, times(10)).save(any(DayBudget.class));
    }

    @Test
    void createRandomTransactions_skips_whenDaysExist() {
        when(categoryRepository.countByOwner(user)).thenReturn(9L);
        when(dayBudgetRepository.countByOwner(user)).thenReturn(10L);

        service.createRandomTransactions(user, 5);

        verify(dayBudgetRepository, never()).save(any(DayBudget.class));
    }

    @Test
    void createRandomTransactions_createsDaysWithTransactions() {
        when(categoryRepository.countByOwner(user)).thenReturn(9L);
        when(dayBudgetRepository.countByOwner(user)).thenReturn(0L);

        doAnswer(inv -> {
            String name = inv.getArgument(0);
            Category c = new Category();
            c.setId((long) Math.abs(name.hashCode()));
            c.setName(name);
            c.setType("EXPENSE");
            c.setOwner(user);
            return Optional.of(c);
        }).when(categoryRepository).findByNameAndOwner(anyString(), eq(user));

        when(dayBudgetRepository.findByDateAndOwner(any(), eq(user)))
                .thenReturn(Optional.empty());
        when(dayBudgetRepository.save(any(DayBudget.class)))
                .thenAnswer(inv -> {
                    DayBudget b = inv.getArgument(0);
                    if (b.getId() == null) b.setId(100L);
                    return b;
                });

        service.createRandomTransactions(user, 3);

        // 3 дні × 2 збереження = 6
        verify(dayBudgetRepository, times(6)).save(any(DayBudget.class));
        // Хоча б одна транзакція створена (випадкова, але завжди > 0)
        verify(expenseRepository, atLeast(1)).save(any());
        verify(incomeRepository, atLeast(1)).save(any());
    }

}