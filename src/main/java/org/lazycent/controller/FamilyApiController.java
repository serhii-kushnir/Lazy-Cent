package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.User;
import org.lazycent.service.FamilyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyApiController extends BaseController {

    private final FamilyService familyService;

    @GetMapping("/data")
    public ResponseEntity<?> getData(Authentication auth) {
        User user = getCurrentUser(auth);
        if (user == null) return ResponseEntity.status(401).build();

        User freshUser = userRepository.findById(user.getId()).orElse(user);
        Map<String, Object> res = new HashMap<>();
        res.put("currentUserId", freshUser.getId());

        // ДОДАНО: Витягуємо та очищаємо системне повідомлення
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
            res.put("pendingRequest", pending != null ? Map.of("familyName", pending.getFamily().getName()) : null);
            res.put("rejectedRequest", rejected != null ? Map.of("familyName", rejected.getFamily().getName()) : null);
        } else {
            res.put("inFamily", true);
            res.put("familyName", family.getName());
            res.put("inviteCode", family.getInviteCode());
            res.put("isOwner", family.getOwner().getId().equals(freshUser.getId()));

            List<Map<String, Object>> members = familyService.getFamilyMembers(freshUser).stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("email", u.getEmail() != null ? u.getEmail() : "");
                m.put("isOwner", u.getId().equals(family.getOwner().getId()));
                return m;
            }).collect(Collectors.toList());
            res.put("members", members);

            if (family.getOwner().getId().equals(freshUser.getId())) {
                List<Map<String, Object>> reqs = familyService.getPendingRequests(freshUser).stream().map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("username", r.getUser().getUsername());
                    m.put("email", r.getUser().getEmail() != null ? r.getUser().getEmail() : "");
                    return m;
                }).collect(Collectors.toList());
                res.put("pendingRequests", reqs);
            } else {
                res.put("pendingRequests", List.of());
            }
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestParam String name, Authentication auth) {
        try {
            familyService.createFamily(getCurrentUser(auth), name);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestParam String inviteCode, Authentication auth) {
        try {
            familyService.joinFamily(getCurrentUser(auth), inviteCode);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/cancel-request")
    public ResponseEntity<?> cancelRequest(Authentication auth) {
        try {
            familyService.cancelJoinRequest(getCurrentUser(auth));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/clear-rejected")
    public ResponseEntity<?> clearRejected(Authentication auth) {
        try {
            familyService.clearRejectedRequest(getCurrentUser(auth));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/leave")
    public ResponseEntity<?> leave(Authentication auth) {
        try {
            familyService.leaveFamily(getCurrentUser(auth));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> delete(Authentication auth) {
        try {
            familyService.deleteFamily(getCurrentUser(auth));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/regenerate-code")
    public ResponseEntity<?> regenerate(Authentication auth) {
        try {
            String code = familyService.regenerateInviteCode(getCurrentUser(auth));
            return ResponseEntity.ok(Map.of("success", true, "inviteCode", code));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/update-name")
    public ResponseEntity<?> updateName(@RequestParam String name, Authentication auth) {
        try {
            familyService.updateFamilyName(getCurrentUser(auth), name);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remove/{id}")
    public ResponseEntity<?> remove(@PathVariable Long id, Authentication auth) {
        try {
            familyService.removeMember(getCurrentUser(auth), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/transfer/{id}")
    public ResponseEntity<?> transfer(@PathVariable Long id, Authentication auth) {
        try {
            familyService.transferOwnership(getCurrentUser(auth), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        try {
            familyService.approveRequest(getCurrentUser(auth), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reject/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        try {
            familyService.rejectRequest(getCurrentUser(auth), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}