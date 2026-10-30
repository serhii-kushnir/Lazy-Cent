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
    public String viewFamily(@RequestParam(value = "inviteCode", required = false) String inviteCode,
                             Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        User freshUser = userRepository.findById(user.getId()).orElse(user);

        // 👇 Перевіряємо наявність повідомлення та додаємо в модель 👇
        if (freshUser.getNotificationMessage() != null) {
            model.addAttribute("systemMessage", freshUser.getNotificationMessage());
            freshUser.setNotificationMessage(null);
            userRepository.save(freshUser);
        }

        Family family = familyService.getFamily(freshUser);
        model.addAttribute("family", family);
        model.addAttribute("currentUser", freshUser);
        model.addAttribute("isOwner", family != null && familyService.isOwner(freshUser));

        // 👇 Передаємо код з URL у модель
        if (inviteCode != null && !inviteCode.isBlank()) {
            model.addAttribute("prefilledCode", inviteCode.trim().toUpperCase());
        }

        if (family != null) {
            model.addAttribute("members", familyService.getFamilyMembers(freshUser));
            if (familyService.isOwner(freshUser)) {
                model.addAttribute("pendingRequests", familyService.getPendingRequests(freshUser));
            }
        } else {
            model.addAttribute("myPendingRequest", familyService.getUserPendingRequest(freshUser));
            model.addAttribute("myRejectedRequest", familyService.getUserRejectedRequest(freshUser));
        }
        return "family";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name, Authentication auth, RedirectAttributes ra) {
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
    public String join(@RequestParam String inviteCode, Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.joinFamily(user, inviteCode);
            ra.addFlashAttribute("successMessage", "Запит надіслано! Очікуйте, поки власник сім'ї вас підтвердить.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/cancel-request")
    public String cancelRequest(Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.cancelJoinRequest(user);
            ra.addFlashAttribute("successMessage", "Заявку скасовано. Ви можете створити власну сім'ю або подати нову заявку.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/acknowledge-rejection")
    public String acknowledgeRejection(Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.clearRejectedRequest(user);
            ra.addFlashAttribute("successMessage", "Ви можете подати нову заявку.");
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
    public String remove(@PathVariable Long memberId, Authentication auth, RedirectAttributes ra) {
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

    @PostMapping("/approve/{id}")
    public String approve(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.approveRequest(user, id);
            ra.addFlashAttribute("successMessage", "Учасника додано до сім'ї!");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/reject/{id}")
    public String reject(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.rejectRequest(user, id);
            ra.addFlashAttribute("successMessage", "Запит відхилено.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @GetMapping("/shared")
    public String viewShared(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication, Model model) {
        User user = getCurrentUser(authentication);
        if (user == null) return "redirect:/login";

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        if (freshUser.getFamilyId() == null) return "redirect:/family";

        if (startDate == null) startDate = LocalDate.now().minusDays(30);
        if (endDate == null)   endDate   = LocalDate.now();

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("transactions", familyService.getSharedTransactions(freshUser, startDate, endDate));
        model.addAttribute("familyMembers", familyService.getFamilyMembers(freshUser));
        model.addAttribute("currentUser", freshUser); // 👈 Обов'язково
        return "family-shared";
    }

    @PostMapping("/update-name")
    public String updateFamilyName(@RequestParam String name,
                                   Authentication auth,
                                   RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.updateFamilyName(user, name);
            ra.addFlashAttribute("successMessage", msg("toast.family.updated"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/transfer/{id}")
    public String transferOwnership(@PathVariable Long id,
                                    Authentication auth,
                                    RedirectAttributes ra) {
        User user = getCurrentUser(auth);
        if (user == null) return "redirect:/login";
        try {
            familyService.transferOwnership(user, id);
            ra.addFlashAttribute("successMessage", msg("toast.family.transferred"));
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }
}