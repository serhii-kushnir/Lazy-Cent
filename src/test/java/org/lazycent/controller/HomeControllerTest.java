package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.BudgetService;
import org.lazycent.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private BudgetService budgetService;
    @MockBean private CategoryService categoryService;
    @MockBean private DayBudgetRepository dayBudgetRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("home_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);

        when(budgetService.getTodaySummary(any())).thenReturn(new org.lazycent.model.TotalBudget());
        when(budgetService.getWeekSummary(any())).thenReturn(new org.lazycent.model.TotalBudget());
        when(budgetService.getMonthSummary(any())).thenReturn(new org.lazycent.model.TotalBudget());
        when(categoryService.getExpenseCategories(any())).thenReturn(java.util.List.of());
        when(categoryService.getIncomeCategories(any())).thenReturn(java.util.List.of());
        when(dayBudgetRepository.countByOwner(any())).thenReturn(0L);
    }

    @Test
    void allDays_marksLimited_whenTotalExceedsLoaded() throws Exception {
        // Створюємо DayBudget З dayName — інакше Thymeleaf впаде на
        // dayNames[null] (SpEL NPE).
        org.lazycent.model.DayBudget budget = new org.lazycent.model.DayBudget();
        budget.setId(1L);
        budget.setDate(java.time.LocalDate.of(2026, 10, 5));
        budget.setDayName("Понеділок");

        when(budgetService.getAllBudgets(testUser)).thenReturn(java.util.List.of(budget));
        when(dayBudgetRepository.countByOwner(testUser)).thenReturn(200L);

        mockMvc.perform(get("/all-days").with(user(testUser)))
                .andExpect(model().attribute("isLimited", true))
                .andExpect(model().attribute("totalDays", 200L));
    }

    @Test
    void totals_rendersView_forAuthenticated() throws Exception {
        when(budgetService.getTotalBudget(any(), any(), eq(testUser)))
                .thenReturn(new org.lazycent.model.TotalBudget());

        mockMvc.perform(get("/totals").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("totals"))
                .andExpect(model().attributeExists("totalBudget"))
                .andExpect(model().attributeExists("startDate"))
                .andExpect(model().attributeExists("endDate"));
    }

    @Test
    void totals_acceptsExplicitDateRange() throws Exception {
        when(budgetService.getTotalBudget(any(), any(), eq(testUser)))
                .thenReturn(new org.lazycent.model.TotalBudget());

        mockMvc.perform(get("/totals")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("totals"))
                .andExpect(model().attributeExists("startDate"))
                .andExpect(model().attributeExists("endDate"));
    }

    // ==================== "/" ====================

    @Test
    void index_showsLanding_forAnonymous() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void index_redirectsToHome_forAuthenticated() throws Exception {
        mockMvc.perform(get("/").with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

    // ==================== "/home" ====================

    @Test
    void home_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void home_rendersHomePage_forAuthenticated() throws Exception {
        mockMvc.perform(get("/home").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attributeExists("todaySummary"))
                .andExpect(model().attributeExists("weekSummary"))
                .andExpect(model().attributeExists("monthSummary"))
                .andExpect(model().attributeExists("today"))
                .andExpect(model().attributeExists("showDemoBanner"));
    }

    @Test
    void home_showsDemoBanner_whenNoDays() throws Exception {
        when(dayBudgetRepository.countByOwner(testUser)).thenReturn(0L);

        mockMvc.perform(get("/home").with(user(testUser)))
                .andExpect(model().attribute("showDemoBanner", true));
    }

    @Test
    void home_hidesDemoBanner_whenDaysExist() throws Exception {
        when(dayBudgetRepository.countByOwner(testUser)).thenReturn(5L);

        mockMvc.perform(get("/home").with(user(testUser)))
                .andExpect(model().attribute("showDemoBanner", false));
    }

    // ==================== "/all-days" ====================

    @Test
    void allDays_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(get("/all-days"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void allDays_rendersView_forAuthenticated() throws Exception {
        when(budgetService.getAllBudgets(testUser)).thenReturn(java.util.List.of());
        when(dayBudgetRepository.countByOwner(testUser)).thenReturn(0L);

        mockMvc.perform(get("/all-days").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("all-days"))
                .andExpect(model().attributeExists("allBudgets"))
                .andExpect(model().attributeExists("totalDays"))
                .andExpect(model().attributeExists("isLimited"));
    }



    // ==================== "/totals" ====================

    @Test
    void totals_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(get("/totals"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void index_forwardsFlashToHome_whenAuthenticated() throws Exception {
        org.springframework.web.servlet.FlashMap flashMap =
                new org.springframework.web.servlet.FlashMap();
        flashMap.put("successMessage", "test flash");

        org.springframework.web.servlet.FlashMapManager flashMapManager =
                new org.springframework.web.servlet.support.SessionFlashMapManager();

        // Використовуємо RequestContextUtils для встановлення flash
        mockMvc.perform(get("/").with(user(testUser))
                        .flashAttrs(java.util.Map.of("successMessage", "test flash")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

}