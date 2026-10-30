package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.User;
import org.lazycent.service.FamilyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/family")
public class FamilyController extends BaseController {

    private final FamilyService familyService;

    @GetMapping
    public String familyRedirect() {
        return "redirect:/family/shared";
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
            // Якщо не в сім'ї, передаємо порожні дані, щоб уникнути помилок у шаблоні
            model.addAttribute("startDate", LocalDate.now().minusDays(30));
            model.addAttribute("endDate", LocalDate.now());
            model.addAttribute("transactions", java.util.List.of());
            model.addAttribute("familyMembers", java.util.List.of());
            model.addAttribute("currentUser", freshUser);
            return "family-shared";
        }

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null)   endDate   = LocalDate.now();

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("transactions", familyService.getSharedTransactions(freshUser, startDate, endDate));
        model.addAttribute("familyMembers", familyService.getFamilyMembers(freshUser));
        model.addAttribute("currentUser", freshUser);
        return "family-shared";
    }
}