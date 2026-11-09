package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.lazycent.service.FamilyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API для керування сім'єю.
 *
 * <p><b>Обробка помилок:</b> делегується {@link org.lazycent.config.RestApiExceptionHandler}
 * ({@code @RestControllerAdvice} без {@code assignableTypes}, застосовується до всіх
 * REST-контролерів). Бізнес-винятки автоматично перетворюються на {@code ApiErrorDto}
 * з коректним HTTP-статусом (400, 403, 409, 500).
 *
 * <p><b>Чому без try-catch:</b> попередня версія ловила {@code Exception} і повертала
 * {@code Map.of("error", e.getMessage())}. Дві проблеми:
 * <ol>
 *   <li>{@code Map.of} кидає NPE при {@code value == null}. Клієнт отримував 500
 *       замість 400.</li>
 *   <li>Формат {@code {error: "..."}} відрізнявся від {@code ApiErrorDto}
 *       інших REST-контролерів.</li>
 * </ol>
 *
 * <p><b>Principal refresh:</b> після операцій, що змінюють {@code familyId}
 * (create, leave, delete), оновлюємо {@code SecurityContext} свіжим {@code User}
 * з БД. Це дозволяє {@link org.lazycent.config.GlobalControllerAdvice#hasFamily()}
 * НЕ робити SELECT на кожен рендер (див. P1.16).
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyApiController extends BaseController {

    private final FamilyService familyService;

    // ==================== READ ====================

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
            res.put("isOwner", family.getOwner().getId().equals(freshUser.getId()));

            List<Map<String, Object>> members = familyService.getFamilyMembers(freshUser).stream()
                    .map(u -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", u.getId());
                        m.put("username", u.getUsername());
                        m.put("email", u.getEmail() != null ? u.getEmail() : "");
                        m.put("isOwner", u.getId().equals(family.getOwner().getId()));
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

    // ==================== MUTATIONS ====================

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestParam String name, Authentication auth) {
        familyService.createFamily(currentUserOrFail(auth), name);
        refreshPrincipal(auth);   // familyId змінено → оновлюємо сесію
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestParam String inviteCode, Authentication auth) {
        // familyId НЕ змінюється (тільки заявка) → refresh не потрібен
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
        refreshPrincipal(auth);   // familyId = null → оновлюємо сесію
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(Authentication auth) {
        familyService.deleteFamily(currentUserOrFail(auth));
        refreshPrincipal(auth);   // familyId = null (у власника теж) → оновлюємо сесію
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/regenerate-code")
    public ResponseEntity<?> regenerate(Authentication auth) {
        // inviteCode не в principal → refresh не потрібен
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
        // Поточний юзер — власник, він не видаляє себе → familyId не змінюється
        familyService.removeMember(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/transfer/{id}")
    public ResponseEntity<?> transfer(@PathVariable Long id, Authentication auth) {
        // Власник передає права — обидва залишаються в сім'ї → familyId не змінюється
        familyService.transferOwnership(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        // Схвалює власник; familyId власника не змінюється
        familyService.approveRequest(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        familyService.rejectRequest(currentUserOrFail(auth), id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ==================== HELPERS ====================

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

    /**
     * Оновлює {@link SecurityContext} свіжим {@code User} з БД.
     *
     * <p><b>Навіщо:</b> після {@code createFamily / leaveFamily / deleteFamily}
     * змінюється {@code user.familyId}, але {@code Authentication.principal}
     * у сесії залишається старим (detached entity). Наступний рендер сторінки
     * читав би старе значення з principal, поки {@code GlobalControllerAdvice.hasFamily()}
     * не зробить SELECT.
     *
     * <p><b>Збереження типу principal:</b> якщо поточний principal — це
     * {@link CustomOAuth2User}, обгортаємо свіжий {@code User} у новий
     * {@code CustomOAuth2User}, щоб не втратити OAuth2-атрибути ({@code sub}, {@code name},
     * etc.), до яких може звертатися {@code OAuth2User#getAttributes()}.
     * Для звичайних (form-login) користувачів principal стає {@code User}.
     *
     * <p><b>Збереження в сесії:</b> {@code SecurityContextPersistenceFilter}
     * у {@code finally} записує {@link SecurityContextHolder#getContext()} назад
     * у сесію, тому зміна автоматично персиститься.
     */
    private void refreshPrincipal(Authentication auth) {
        User current = getCurrentUser(auth);
        if (current == null) {
            return;
        }

        User fresh = userRepository.findById(current.getId()).orElse(current);

        Object oldPrincipal = auth.getPrincipal();
        Object newPrincipal;
        if (oldPrincipal instanceof CustomOAuth2User oAuth2User) {
            newPrincipal = new CustomOAuth2User(oAuth2User.getOAuth2User(), fresh);
        } else {
            newPrincipal = fresh;
        }

        Authentication newAuth = new UsernamePasswordAuthenticationToken(
                newPrincipal,
                auth.getCredentials(),
                fresh.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }
}