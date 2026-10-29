package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.lazycent.service.FamilyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/family")
@Slf4j
public class FamilyController extends BaseController {

    private final FamilyService familyService;

    @GetMapping
    public String viewFamily(Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        // 👇 ГОЛОВНИЙ ФІКС: Беремо свіжі дані з БД, ігноруючи стару сесію
        User freshUser = userRepository.findById(user.getId()).orElse(user);

        Family family = familyService.getFamily(freshUser);
        model.addAttribute("family", family);
        model.addAttribute("currentUser", freshUser);
        model.addAttribute("isOwner", family != null && familyService.isOwner(freshUser));
        if (family != null) {
            model.addAttribute("members", familyService.getFamilyMembers(freshUser));
        }
        return "family";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name,
                         Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.createFamily(user, name);
            ra.addFlashAttribute("successMessage", msg("toast.family.created"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/join")
    public String join(@RequestParam String inviteCode,
                       Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.joinFamily(user, inviteCode);
            ra.addFlashAttribute("successMessage", msg("toast.family.joined"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/leave")
    public String leave(Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.leaveFamily(user);
            ra.addFlashAttribute("successMessage", msg("toast.family.left"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/remove/{memberId}")
    public String remove(@PathVariable Long memberId,
                         Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.removeMember(user, memberId);
            ra.addFlashAttribute("successMessage", msg("toast.family.memberRemoved"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/regenerate-code")
    public String regenerate(Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.regenerateInviteCode(user);
            ra.addFlashAttribute("successMessage", msg("toast.family.codeRegenerated"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/delete")
    public String delete(Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.deleteFamily(user);
            ra.addFlashAttribute("successMessage", msg("toast.family.deleted"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @GetMapping("/shared")
    public String viewShared(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        // 👇 Беремо свіжі дані з БД
        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (freshUser.getFamilyId() == null) return "redirect:/family";

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null)   endDate   = LocalDate.now();

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("transactions",
                familyService.getSharedTransactions(freshUser, startDate, endDate));
        return "family-shared";
    }
}