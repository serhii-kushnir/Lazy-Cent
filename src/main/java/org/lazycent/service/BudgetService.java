package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.*;
import org.lazycent.model.dto.CategorySummaryDto;
import org.lazycent.model.dto.DailySummaryDto;
import org.lazycent.model.dto.TransactionRequest;
import org.lazycent.repository.CategoryRepository;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final DayBudgetRepository dayBudgetRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    private static final Map<Integer, String> DAY_NAMES = Map.of(
            1, "Понеділок", 2, "Вівторок", 3, "Середа", 4, "Четвер",
            5, "П'ятниця", 6, "Субота", 7, "Неділя"
    );

    // ==================== ОСНОВНІ МЕТОДИ ====================

    @Transactional
    public DayBudget getOrCreateDayBudget(LocalDate date) {
        log.debug("Getting or creating day budget for date: {}", date);
        return dayBudgetRepository.findByDate(date)
                .orElseGet(() -> {
                    log.info("Creating new day budget for date: {}", date);
                    DayBudget newBudget = new DayBudget();
                    newBudget.setDate(date);
                    newBudget.setDayName(getDayName(date));
                    newBudget.setStartingBalance(getLastRemainingBalance(date));
                    newBudget.setRemainingBalance(0.0);
                    DayBudget saved = dayBudgetRepository.save(newBudget);
                    updateDayBudgetBalance(saved);
                    log.info("Day budget created with ID: {}", saved.getId());
                    return saved;
                });
    }

    private Double getLastRemainingBalance(LocalDate currentDate) {
        List<DayBudget> allBudgets = dayBudgetRepository.findAllByOrderByDateAsc();
        if (allBudgets.isEmpty()) return 0.0;
        return allBudgets.stream()
                .filter(d -> d.getDate().isBefore(currentDate))
                .reduce((first, second) -> second)
                .map(DayBudget::getRemainingBalance)
                .orElse(0.0);
    }

    private String getDayName(LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        return DAY_NAMES.getOrDefault(dayOfWeek, "Невідомо");
    }

    // ==================== ДОДАВАННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public void addTransaction(TransactionRequest request, TransactionType type) {
        log.info("Adding {} transaction: date={}, categoryId={}, amount={}, description='{}'",
                type, request.getDate(), request.getCategoryId(), request.getAmount(), request.getDescription());

        LocalDate date = request.getDate();
        DayBudget dayBudget = getOrCreateDayBudget(date);
        Category category = getCategoryById(request.getCategoryId());

        Transaction transaction = new Transaction();
        transaction.setType(type);
        transaction.setCategory(category);
        transaction.setDescription(request.getDescription());
        transaction.setAmount(request.getAmount());
        transaction.setQuantity(request.getQuantity() != null ? request.getQuantity() : 1);
        transaction.setDetails(request.getDetails());
        transaction.setShareWithFamily(request.getShareWithFamily() != null && request.getShareWithFamily());
        transaction.setTime(request.getTime());
        transaction.setBuyer(request.getBuyer());
        transaction.setSeller(request.getSeller());
        transaction.setDayBudget(dayBudget);

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction saved with ID: {}", saved.getId());
        updateDayBudgetBalance(dayBudget);
    }

    // ==================== ОНОВЛЕННЯ БАЛАНСІВ ====================

    private void updateDayBudgetBalance(DayBudget dayBudget) {
        log.debug("Updating balance for day: {}", dayBudget.getDate());
        Double totalExpenses = transactionRepository.sumByDateAndType(dayBudget.getDate(), TransactionType.EXPENSE);
        Double totalIncomes = transactionRepository.sumByDateAndType(dayBudget.getDate(), TransactionType.INCOME);

        if (totalExpenses == null) totalExpenses = 0.0;
        if (totalIncomes == null) totalIncomes = 0.0;

        Double previousBalance = dayBudget.getStartingBalance() != null ? dayBudget.getStartingBalance() : 0.0;
        Double newBalance = previousBalance + totalIncomes - totalExpenses;
        dayBudget.setRemainingBalance(newBalance);
        dayBudgetRepository.save(dayBudget);
        log.debug("Day {} balance updated: previous={}, expenses={}, incomes={}, new={}",
                dayBudget.getDate(), previousBalance, totalExpenses, totalIncomes, newBalance);

        updateNextDaysBalances(dayBudget.getDate());
    }

    private void updateNextDaysBalances(LocalDate fromDate) {
        log.debug("Updating balances for days after: {}", fromDate);
        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> b.getDate().isAfter(fromDate))
                .toList();

        Double previousBalance = null;
        for (DayBudget budget : nextBudgets) {
            if (previousBalance == null) {
                DayBudget previousDay = dayBudgetRepository.findByDate(budget.getDate().minusDays(1)).orElse(null);
                if (previousDay != null) {
                    previousBalance = previousDay.getRemainingBalance();
                }
            }
            if (previousBalance != null) {
                budget.setStartingBalance(previousBalance);
                Double totalExpenses = transactionRepository.sumByDateAndType(budget.getDate(), TransactionType.EXPENSE);
                Double totalIncomes = transactionRepository.sumByDateAndType(budget.getDate(), TransactionType.INCOME);
                if (totalExpenses == null) totalExpenses = 0.0;
                if (totalIncomes == null) totalIncomes = 0.0;
                budget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
                dayBudgetRepository.save(budget);
                previousBalance = budget.getRemainingBalance();
            }
        }
        log.debug("Balances updated for {} days", nextBudgets.size());
    }

    // ==================== ОТРИМАННЯ ДАНИХ ====================

    @Transactional(readOnly = true)
    public TotalBudget getTotalBudget(LocalDate startDate, LocalDate endDate) {
        log.info("Getting total budget for period: {} - {}", startDate, endDate);
        TotalBudget total = new TotalBudget();

        // 1. Отримуємо щоденні підсумки (1 запит)
        List<DailySummaryDto> dailySummariesDto = transactionRepository.getDailySummaries(startDate, endDate);

        // 2. Отримуємо підсумки за категоріями (1 запит)
        List<CategorySummaryDto> categorySummaries = transactionRepository.getCategorySummaries(startDate, endDate);

        // Формуємо результат
        Map<LocalDate, TotalBudget.DayBudgetSummary> dailySummariesMap = new LinkedHashMap<>();
        Double totalExpenses = 0.0;
        Double totalIncomes = 0.0;

        for (DailySummaryDto dto : dailySummariesDto) {
            TotalBudget.DayBudgetSummary summary = new TotalBudget.DayBudgetSummary();
            summary.setDate(dto.getDate());
            summary.setDayName(dto.getDayName());
            summary.setExpenses(dto.getTotalExpenses());
            summary.setIncomes(dto.getTotalIncomes());
            summary.setBalance(dto.getTotalIncomes() - dto.getTotalExpenses());
            dailySummariesMap.put(dto.getDate(), summary);
            totalExpenses += dto.getTotalExpenses();
            totalIncomes += dto.getTotalIncomes();
        }

        // Категорії
        Map<Category, Double> expensesByCategory = new HashMap<>();
        Map<Category, Double> incomesByCategory = new HashMap<>();

        for (CategorySummaryDto dto : categorySummaries) {
            Category category = categoryRepository.findByName(dto.getCategoryName()).orElse(null);
            if (category != null) {
                if (dto.getType() == TransactionType.EXPENSE) {   // ✅ тепер коректно
                    expensesByCategory.put(category, dto.getTotalAmount());
                } else {
                    incomesByCategory.put(category, dto.getTotalAmount());
                }
            }
        }

        total.setTotalExpenses(totalExpenses);
        total.setTotalIncomes(totalIncomes);
        total.setBalance(totalIncomes - totalExpenses);
        total.setExpensesByCategory(expensesByCategory);
        total.setIncomesByCategory(incomesByCategory);
        total.setDailySummaries(dailySummariesMap);

        log.info("Total budget: expenses={}, incomes={}, balance={}", totalExpenses, totalIncomes, total.getBalance());
        return total;
    }

    public List<DayBudget> getAllBudgets() {
        log.debug("Retrieving all day budgets");
        return dayBudgetRepository.findAllByOrderByDateAsc();
    }

    // ==================== ДУБЛЮВАННЯ ТРАНЗАКЦІЙ ====================

    @Transactional
    public LocalDate duplicateTransaction(Long transactionId) {
        log.info("Duplicating transaction with ID: {}", transactionId);
        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Транзакцію не знайдено"));

        Transaction duplicate = new Transaction();
        duplicate.setDayBudget(original.getDayBudget());
        duplicate.setCategory(original.getCategory());
        duplicate.setType(original.getType());
        duplicate.setDescription(original.getDescription() + " (копія)");
        duplicate.setAmount(original.getAmount());
        duplicate.setQuantity(original.getQuantity());
        duplicate.setDetails(original.getDetails());
        duplicate.setTime(original.getTime());
        duplicate.setBuyer(original.getBuyer());
        duplicate.setSeller(original.getSeller());
        duplicate.setShareWithFamily(original.getShareWithFamily());

        transactionRepository.save(duplicate);
        log.info("Transaction duplicated with new ID: {}", duplicate.getId());
        updateDayBudgetBalance(original.getDayBudget());
        return original.getDayBudget().getDate();
    }

    // ==================== КАТЕГОРІЇ ====================

    public List<Category> getExpenseCategories() {
        return categoryRepository.findByType("EXPENSE");
    }

    public List<Category> getIncomeCategories() {
        return categoryRepository.findByType("INCOME");
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional
    public Category addCategory(String name, String type) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new RuntimeException("Назва категорії не може бути порожньою");
        }

        if (categoryRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new RuntimeException("Категорія з таким ім'ям вже існує");
        }

        Category category = new Category();
        category.setName(normalizedName);
        category.setType(type.toUpperCase());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
        categoryRepository.deleteById(id);
    }

    @Transactional
    public Category updateCategory(Long id, String newName) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));

        String normalizedName = newName.trim();
        if (normalizedName.isEmpty()) {
            throw new RuntimeException("Назва категорії не може бути порожньою");
        }

        categoryRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new RuntimeException("Категорія з таким ім'ям вже існує");
                    }
                });

        category.setName(normalizedName);
        return categoryRepository.save(category);
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
    }

    // ==================== РЕДАГУВАННЯ/ВИДАЛЕННЯ ====================

    @Transactional
    public void deleteTransaction(Long transactionId) {
        log.info("Deleting transaction with ID: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Транзакцію не знайдено"));
        DayBudget dayBudget = transaction.getDayBudget();
        transactionRepository.delete(transaction);
        log.info("Transaction ID: {} deleted", transactionId);
        updateDayBudgetBalance(dayBudget);
    }

    @Transactional
    public Transaction updateTransaction(Long transactionId, String description, Double amount, Long categoryId,
                                         Integer quantity, String details, Boolean shareWithFamily, LocalTime time,
                                         String buyer, String seller) {
        log.info("Updating transaction ID: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Транзакцію не знайдено"));

        transaction.setDescription(description);
        transaction.setAmount(amount);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Категорію не знайдено"));
            transaction.setCategory(category);
        }
        transaction.setQuantity(quantity != null ? quantity : 1);
        transaction.setDetails(details);
        transaction.setShareWithFamily(shareWithFamily != null && shareWithFamily);
        transaction.setTime(time);
        transaction.setBuyer(buyer);
        transaction.setSeller(seller);

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction ID: {} updated", transactionId);
        updateDayBudgetBalance(transaction.getDayBudget());
        return saved;
    }

    public LocalDate getDateByTransaction(Long transactionId) {
        return transactionRepository.findDateById(transactionId);
    }

    @Transactional
    public void deleteDay(Long dayBudgetId) {
        log.info("Deleting day budget with ID: {}", dayBudgetId);
        DayBudget dayBudget = dayBudgetRepository.findById(dayBudgetId)
                .orElseThrow(() -> new RuntimeException("День не знайдено"));

        LocalDate deletedDate = dayBudget.getDate();
        dayBudgetRepository.delete(dayBudget);
        log.info("Day budget {} deleted", dayBudgetId);

        List<DayBudget> nextBudgets = dayBudgetRepository.findAllByOrderByDateAsc().stream()
                .filter(b -> b.getDate().isAfter(deletedDate))
                .toList();

        if (nextBudgets.isEmpty()) return;

        DayBudget previousDay = dayBudgetRepository.findByDate(deletedDate.minusDays(1)).orElse(null);
        Double previousBalance = previousDay != null ? previousDay.getRemainingBalance() : 0.0;

        for (DayBudget budget : nextBudgets) {
            budget.setStartingBalance(previousBalance);
            Double totalExpenses = transactionRepository.sumByDateAndType(budget.getDate(), TransactionType.EXPENSE);
            Double totalIncomes = transactionRepository.sumByDateAndType(budget.getDate(), TransactionType.INCOME);
            if (totalExpenses == null) totalExpenses = 0.0;
            if (totalIncomes == null) totalIncomes = 0.0;
            budget.setRemainingBalance(previousBalance + totalIncomes - totalExpenses);
            dayBudgetRepository.save(budget);
            previousBalance = budget.getRemainingBalance();
        }
    }
}