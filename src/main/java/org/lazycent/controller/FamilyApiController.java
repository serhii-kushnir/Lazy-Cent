package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.lazycent.repository.FamilyRepository;
import org.lazycent.service.FamilyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API для керування сім'єю.
 *
 * <p><b>Обробка помилок:</b> делегується {@code RestApiExceptionHandler}
 * ({@code @RestControllerAdvice}). Всі бізнес-винятки
 * ({@code ValidationException}, {@code BusinessException}) автоматично
 * перетворюються на {@code ApiErrorDto} з коректним HTTP-статусом
 * (400, 403, 409, 500).
 *
 * <p><b>Чому без try-catch:</b> попередня версія ловила {@code Exception}
 * і повертала {@code Map.of("error", e.getMessage())}. Дві проблеми:
 * <ol>
 *   <li>{@code Map.of} кидає NPE при {@code value == null}, якщо
 *       {@code e.getMessage() == null}. Клієнт отримував 500 замість 400.</li>
 *   <li>Формат {@code {error: "..."}} відрізнявся від {@code ApiErrorDto},
 *       який повертали інші REST-контролери.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyApiController extends BaseController {

    private final FamilyService familyService;
    @Autowired
    private FamilyRepository familyRepository;

    @GetMapping("/data")
    public ResponseEntity<?> getData(Authentication auth) {
        User user = currentUserOrFail(auth);

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        Map<String, Object> res = new HashMap<>();
        res.put("currentUserId", freshUser.getId());

        // Системне повідомлення — «знімаємо» після прочитання
        if (freshUser.getNotificationMessage() != null) {
            res.put("systemMessage", freshUser.getNotificationMessage());
            freshUser.setNotificationMessage(null);
            userRepository.save(freshUser);
        }

        Family family = familyService.getFamily(freshUser);
        if (family == null) {
            res.put("inFamily", false);
            var pending = familyService.getUserPendingRequest(freshUser);
            var rejected = familyService.getUserRejectedRequest(freshUser);
            res.put("pendingRequest",
                    pending != null ? Map.of("familyName", pending.getFamily().getName()) : null);
            res.put("rejectedRequest",
                    rejected != null ? Map.of("familyName", rejected.getFamily().getName()) : null);
        } else {
            res.put("inFamily", true);
            res.put("familyName", family.getName());
            res.put("inviteCode", family.getInviteCode());
            Long ownerId = familyRepository.findOwnerIdByFamilyId(family.getId())
                    .orElse(freshUser.getId());

            res.put("isOwner", ownerId.equals(freshUser.getId()));

            List<Map<String, Object>> members = familyService.getFamilyMembers(freshUser).stream()
                    .map(u -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", u.getId());
                        m.put("username", u.getUsername());
                        m.put("email", u.getEmail() != null ? u.getEmail() : "");
                        m.put("isOwner", ownerId.equals(u.getId()));
                        return m;
                    })
                    .collect(Collectors.toList());
            res.put("members", members);

            if (family.getOwner().getId().equals(freshUser.getId())) {
                List<Map<String, Object>> reqs = familyService.getPendingRequests(freshUser).stream()
                        .map(r -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", r.getId());
                            m.put("username", r.getUser().getUsername());
                            m.put("email", r.getUser().getEmail() != null ? r.getUser().getEmail() : "");
                            return m;
                        })
                        .collect(Collectors.toList());
                res.put("pendingRequests", reqs);
            } else {
                res.put("pendingRequests", List.of());
            }
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestParam String name, Authentication auth) {
        familyService.createFamily(currentUserOrFail(auth), name);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestParam String inviteCode, Authentication auth) {
        familyService.joinFamily(currentUserOrFail(auth), inviteCode);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/cancel-request")
    public ResponseEntity<?> cancelRequest(Authentication auth) {
        familyService.cancelJoinRequest(currentUserOrFail(auth));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/clear-rejected")
    public ResponseEntity<?> clearRejected(Authentication auth) {
        familyService.clearRejectedRequest(currentUserOrFail(auth));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leave(Authentication auth) {
        familyService.leaveFamily(currentUserOrFail(auth));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(Authentication auth) {
        familyService.deleteFamily(currentUserOrFail(auth));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/regenerate-code")
    public ResponseEntity<?> regenerate(Authentication auth) {
        String code = familyService.regenerateInviteCode(currentUserOrFail(auth));
        return ResponseEntity.ok(Map.of("success", true, "inviteCode", code));
    }

    @PostMapping("/update-name")
    public ResponseEntity<?> updateName(@RequestParam String name, Authentication auth) {
        familyService.updateFamilyName(currentUserOrFail(auth), name);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/remove/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id, Authentication auth) {
        familyService.removeMember(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/transfer/{id}")
    public ResponseEntity<?> transfer(@PathVariable Long id, Authentication auth) {
        familyService.transferOwnership(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        familyService.approveRequest(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        familyService.rejectRequest(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Повертає поточного користувача або кидає {@link AccessDeniedException}.
     * Останній перетворюється на 403 з {@code ApiErrorDto} у
     * {@code RestApiExceptionHandler}.
     */
    private User currentUserOrFail(Authentication auth) {
        User user = getCurrentUser(auth);
        if (user == null) {
            throw new AccessDeniedException("Користувача не автентифіковано");
        }
        return user;
    }
}