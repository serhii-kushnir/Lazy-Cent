package org.lazycent.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.lazycent.model.dto.SharedDayDto;
import org.lazycent.model.dto.SharedMemberSummaryDto;
import org.lazycent.model.dto.SharedTransactionDto;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.FamilyService;
import org.lazycent.service.FamilySharedDayExportService;
import org.lazycent.service.FamilySharedExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/family")
public class FamilyController extends BaseController {

    private final FamilyService familyService;
    private final DayBudgetRepository dayBudgetRepository;
    private final FamilySharedDayExportService familySharedDayExportService;
    private final FamilySharedExportService familySharedExportService;
    private final UserRepository userRepository;

    private static String nullSafe(String s) { return s == null ? "" : s; }

    // ==================== HUB ====================

    @GetMapping
    public String viewFamilyHub(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        Family family = familyService.getFamily(freshUser);

        if (family == null) {
            return "redirect:/family/shared";
        }

        model.addAttribute("family", family);
        model.addAttribute("membersCount", familyService.getFamilyMembers(freshUser).size());
        return "family/hub";
    }

    // ==================== SHARED — LIST ====================

    @GetMapping("/shared")
    public String viewShared(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String dayName,
            @RequestParam(required = false) String member,
            @RequestParam(required = false) String balance,
            @RequestParam(required = false) String q,
            Authentication authentication, Model model) {

        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        boolean inFamily = freshUser.getFamilyId() != null;
        model.addAttribute("inFamily", inFamily);
        boolean customDateFilter = startDate != null || endDate != null;
        LocalDate defaultStartDate = LocalDate.now().minusMonths(6);
        LocalDate defaultEndDate = LocalDate.now();
        model.addAttribute("defaultStartDate", defaultStartDate);
        model.addAttribute("defaultEndDate", defaultEndDate);

        if (!inFamily) {
            model.addAttribute("startDate", defaultStartDate);
            model.addAttribute("endDate", defaultEndDate);
            model.addAttribute("days", List.of());
            model.addAttribute("totalExpenses", 0.0);
            model.addAttribute("totalIncomes", 0.0);
            model.addAttribute("balance", 0.0);
            model.addAttribute("familyMembers", List.of());
            model.addAttribute("currentUser", freshUser);
            model.addAttribute("page", 0);
            model.addAttribute("pageSize", 30);
            model.addAttribute("totalDays", 0);
            model.addAttribute("hasNext", false);
            model.addAttribute("hasPrevious", false);
            model.addAttribute("memberSummaries", List.of());
            model.addAttribute("chartLabels", List.of());
            model.addAttribute("chartExpenses", List.of());
            model.addAttribute("chartIncomes", List.of());
            model.addAttribute("hasActiveFilters", false);
            return "family/shared";
        }

        if (startDate == null) startDate = defaultStartDate;
        if (endDate == null)   endDate   = defaultEndDate;

        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        boolean hasFilters = (dayName != null && !dayName.isBlank())
                || (member != null && !member.isBlank())
                || (balance != null && !balance.isBlank())
                || (q != null && !q.isBlank());

        List<SharedDayDto> chartDays = familyService.getSharedDaySummaries(freshUser, startDate, endDate);
        Page<SharedDayDto> daysPage;
        List<SharedDayDto> totalsDays;
        if (hasFilters) {
            List<SharedDayDto> matchingDays = filterSharedDays(
                    familyService.getSharedDays(freshUser, startDate, endDate),
                    member, balance, q, dayName);
            totalsDays = matchingDays;
            safePage = Math.min(safePage, Math.max(0, (matchingDays.size() - 1) / safeSize));
            int fromIndex = Math.min(safePage * safeSize, matchingDays.size());
            int toIndex = Math.min(fromIndex + safeSize, matchingDays.size());
            daysPage = new PageImpl<>(matchingDays.subList(fromIndex, toIndex),
                    PageRequest.of(safePage, safeSize), matchingDays.size());
        } else {
            safePage = Math.min(safePage, Math.max(0, (chartDays.size() - 1) / safeSize));
            daysPage = familyService.getSharedDaysPaged(freshUser, startDate, endDate, safePage, safeSize);
            totalsDays = chartDays;
        }
        List<SharedDayDto> days = daysPage.getContent();

        List<SharedMemberSummaryDto> memberSummaries =
                familyService.getSharedMemberSummaries(freshUser, startDate, endDate);

        double totalExp = 0, totalInc = 0;
        for (SharedDayDto d : totalsDays) {
            totalExp += d.totalExpenses();
            totalInc += d.totalIncomes();
        }

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("days", days);
        model.addAttribute("totalExpenses", totalExp);
        model.addAttribute("totalIncomes", totalInc);
        model.addAttribute("balance", totalInc - totalExp);
        model.addAttribute("familyMembers", familyService.getFamilyMembers(freshUser));
        model.addAttribute("memberSummaries", memberSummaries);
        model.addAttribute("chartDays", chartDays);
        model.addAttribute("page", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalDays", daysPage.getTotalElements());
        model.addAttribute("hasNext", daysPage.hasNext());
        model.addAttribute("hasPrevious", daysPage.hasPrevious());
        model.addAttribute("chartLabels", chartDays.stream().map(d -> d.date().toString()).toList());
        model.addAttribute("chartExpenses", chartDays.stream().map(SharedDayDto::totalExpenses).toList());
        model.addAttribute("chartIncomes", chartDays.stream().map(SharedDayDto::totalIncomes).toList());
        model.addAttribute("dayNameFilter", dayName != null ? dayName : "");
        model.addAttribute("memberFilter", member != null ? member : "");
        model.addAttribute("balanceFilter", balance != null ? balance : "");
        model.addAttribute("searchQuery", q != null ? q : "");
        model.addAttribute("hasActiveFilters", customDateFilter || (dayName != null && !dayName.isBlank())
                || (member != null && !member.isBlank()) || (balance != null && !balance.isBlank())
                || (q != null && !q.isBlank()));
        model.addAttribute("currentUser", freshUser);
        return "family/shared";
    }

    // ==================== SHARED — DAY DETAIL ====================

    @GetMapping("/shared/day/{date}")
    public String viewSharedDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication, Model model) {

        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (freshUser.getFamilyId() == null) {
            return "redirect:/family/shared";
        }

        List<SharedTransactionDto> txs = familyService.getSharedTransactionsByDate(freshUser, date);

        double totalExp = 0, totalInc = 0;
        for (SharedTransactionDto t : txs) {
            double amt = t.amount() != null ? t.amount() : 0.0;
            if (t.expense()) totalExp += amt;
            else if (t.income()) totalInc += amt;
        }

        Double startingBalance = dayBudgetRepository
                .sumStartingBalanceByFamilyAndDate(freshUser.getFamilyId(), date);
        if (startingBalance == null) startingBalance = 0.0;

        double balance = startingBalance + totalInc - totalExp;

        LocalDate yesterday = date.minusDays(1);
        double yesterdayExpenses = 0.0;
        for (SharedTransactionDto t : familyService.getSharedTransactionsByDate(freshUser, yesterday)) {
            if (t.expense() && t.amount() != null) yesterdayExpenses += t.amount();
        }

        model.addAttribute("date", date);
        model.addAttribute("dayName", familyService.getDayName(date));
        model.addAttribute("transactions", txs);
        model.addAttribute("startingBalance", startingBalance);
        model.addAttribute("totalExpenses", totalExp);
        model.addAttribute("totalIncomes", totalInc);
        model.addAttribute("balance", balance);
        model.addAttribute("yesterdayExpenses", yesterdayExpenses);
        model.addAttribute("familyMembers", familyService.getFamilyMembers(freshUser));
        model.addAttribute("currentUser", freshUser);
        return "family/shared-day";
    }

    // ==================== SHARED — EXPORT ====================

    @GetMapping("/shared/export")
    public void exportShared(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String member,
            @RequestParam(required = false) String balance,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String dayName,
            Authentication authentication,
            HttpServletResponse response) throws java.io.IOException {

        User user = getCurrentUser(authentication);
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (freshUser.getFamilyId() == null) {
            response.sendRedirect("/family/shared");
            return;
        }

        LocalDate from = startDate != null ? startDate : LocalDate.now().minusMonths(6);
        LocalDate to = endDate != null ? endDate : LocalDate.now();
        List<SharedDayDto> days = familyService.getSharedDays(freshUser, from, to);
        days = filterSharedDays(days, member, balance, q, dayName);
        byte[] xlsx = familySharedExportService.export(days, from, to,
                org.springframework.context.i18n.LocaleContextHolder.getLocale());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"family-shared-" + from + "-to-" + to + ".xlsx\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    @GetMapping("/shared/day/{date}/export")
    public void exportSharedDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) String member,
            @RequestParam(required = false) String q,
            Authentication authentication,
            HttpServletResponse response) throws java.io.IOException {

        User user = getCurrentUser(authentication);
        if (user == null) { response.sendRedirect("/login"); return; }

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (freshUser.getFamilyId() == null) {
            response.sendRedirect("/family/shared");
            return;
        }

        var txs = familyService.getSharedTransactionsByDate(freshUser, date);

        // ✅ Фільтри (винесено у filterShared — єдина логіка)
        txs = filterShared(txs, type, cat, member, q);

        String dayName = familyService.getDayName(date);
        Double startingBalance = dayBudgetRepository
                .sumStartingBalanceByFamilyAndDate(freshUser.getFamilyId(), date);
        if (startingBalance == null) startingBalance = 0.0;

        byte[] xlsx = familySharedDayExportService.export(
                freshUser, date, dayName, txs, startingBalance,
                org.springframework.context.i18n.LocaleContextHolder.getLocale());

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"family-shared-" + date + ".xlsx\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    // ==================== SHARED — POLLING ====================

    @GetMapping("/shared/day/{date}/count")
    @ResponseBody
    public Map<String, Object> getSharedDayCount(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (user == null) return Map.of("count", 0, "maxId", 0L);

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (freshUser.getFamilyId() == null) return Map.of("count", 0, "maxId", 0L);

        var txs = familyService.getSharedTransactionsByDate(freshUser, date);
        long maxId = txs.stream()
                .map(SharedTransactionDto::id)
                .filter(java.util.Objects::nonNull)
                .max(Long::compare)
                .orElse(0L);

        return Map.of("count", txs.size(), "maxId", maxId);
    }

    // ==================== HELPER ====================

    private List<SharedDayDto> filterSharedDays(
            List<SharedDayDto> days, String member, String balance, String q, String dayName) {

        return days.stream().map(day -> {
            List<SharedTransactionDto> txs = day.transactions();
            if (txs == null) txs = List.of();

            if (member != null && !member.isBlank()) {
                txs = txs.stream()
                        .filter(t -> member.equals(t.memberUsername()))
                        .toList();
            }
            if (q != null && !q.isBlank()) {
                String qLower = q.toLowerCase().trim();
                txs = txs.stream().filter(t -> {
                    String hay = String.join(" ",
                            nullSafe(day.dayName()),
                            day.date().toString(),
                            nullSafe(t.description()),
                            nullSafe(t.categoryName()),
                            nullSafe(t.memberUsername()),
                            t.amount() != null ? String.valueOf(t.amount()) : ""
                    ).toLowerCase();
                    return hay.contains(qLower);
                }).toList();
            }

            double expenses = txs.stream().filter(SharedTransactionDto::expense)
                    .mapToDouble(t -> t.amount() != null ? t.amount() : 0.0).sum();
            double incomes = txs.stream().filter(SharedTransactionDto::income)
                    .mapToDouble(t -> t.amount() != null ? t.amount() : 0.0).sum();
            double dayBalance = incomes - expenses;

            return new SharedDayDto(day.date(), day.dayName(), expenses, incomes, dayBalance, txs);
        }).filter(day -> {
            if (day.transactions() == null || day.transactions().isEmpty()) return false;
            if (dayName != null && !dayName.isBlank() && !dayName.equals(day.dayName())) return false;
            if ("positive".equals(balance) && day.balance() < 0) return false;
            if ("negative".equals(balance) && day.balance() >= 0) return false;
            if ("zero".equals(balance) && day.balance() != 0) return false;
            return true;
        }).toList();
    }

    /**
     * Єдина логіка фільтрації shared-транзакцій.
     * Використовується в /export (view фільтрується на клієнті).
     */
    private List<SharedTransactionDto> filterShared(
            List<SharedTransactionDto> txs,
            String type, String cat, String member, String q) {

        if (type != null && !type.isBlank() && !"all".equals(type)) {
            boolean wantExpense = "expense".equalsIgnoreCase(type);
            txs = txs.stream()
                    .filter(t -> wantExpense ? t.expense() : t.income())
                    .toList();
        }
        if (cat != null && !cat.isBlank()) {
            String catNorm = cat.replaceAll("\\s*\\(\\d+\\)$", "").trim();
            txs = txs.stream()
                    .filter(t -> catNorm.equals(t.categoryName()))
                    .toList();
        }
        if (member != null && !member.isBlank() && !"all".equals(member)) {
            txs = txs.stream()
                    .filter(t -> member.equals(t.memberUsername()))
                    .toList();
        }
        if (q != null && !q.isBlank()) {
            String qLower = q.toLowerCase().trim();
            txs = txs.stream().filter(t -> {
                String hay = String.join(" ",
                        nullSafe(t.description()), nullSafe(t.categoryName()),
                        nullSafe(t.memberUsername()), nullSafe(t.buyer()),
                        nullSafe(t.seller()), nullSafe(t.details()),
                        t.amount() != null ? String.valueOf(t.amount()) : ""
                ).toLowerCase();
                return hay.contains(qLower);
            }).toList();
        }
        return txs;
    }
}