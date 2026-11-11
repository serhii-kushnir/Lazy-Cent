package org.lazycent.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.service.FamilyService;
import org.lazycent.service.FamilySharedDayExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.lazycent.model.dto.SharedDayDto;
import org.lazycent.model.dto.SharedTransactionDto;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/family")
public class FamilyController extends BaseController {

    private final FamilyService familyService;
    private final FamilySharedDayExportService familySharedDayExportService;
    private final DayBudgetRepository dayBudgetRepository;


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
        return "family/hub";                       // ← ЗМІНЕНО
    }

    @GetMapping("/shared")
    public String viewShared(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication, Model model) {

        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        boolean inFamily = freshUser.getFamilyId() != null;
        model.addAttribute("inFamily", inFamily);

        if (!inFamily) {
            model.addAttribute("startDate", LocalDate.now().minusMonths(6));
            model.addAttribute("endDate", LocalDate.now());
            model.addAttribute("days", List.of());
            model.addAttribute("totalExpenses", 0.0);
            model.addAttribute("totalIncomes", 0.0);
            model.addAttribute("balance", 0.0);
            model.addAttribute("familyMembers", List.of());
            model.addAttribute("currentUser", freshUser);
            return "family/shared";
        }

        if (startDate == null) startDate = LocalDate.now().minusMonths(6);
        if (endDate == null)   endDate   = LocalDate.now();

        List<SharedDayDto> days = familyService.getSharedDays(freshUser, startDate, endDate);

        double totalExp = 0, totalInc = 0;
        for (SharedDayDto d : days) {
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
        model.addAttribute("currentUser", freshUser);
        return "family/shared";
    }

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

        // ✅ Порівняння з учора
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

    @GetMapping("/shared/day/{date}/export")
    public void exportSharedDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
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
}