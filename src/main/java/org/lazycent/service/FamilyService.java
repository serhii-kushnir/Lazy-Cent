package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Family;
import org.lazycent.model.FamilyJoinRequest;
import org.lazycent.model.User;
import org.lazycent.model.dto.SharedTransactionDto;
import org.lazycent.repository.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FamilyRepository familyRepository;
    private final FamilyJoinRequestRepository joinRequestRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final MessageSource messageSource;

    // ==================== CREATE / JOIN / LEAVE ====================

    @Transactional
    public Family createFamily(User user, String name) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException(msg("error.user.notFound", "Користувача не знайдено")));
        if (managed.getFamily() != null) {
            throw new ValidationException(msg("error.family.alreadyInFamily", "Ви вже є учасником сім'ї"));
        }
        if (joinRequestRepository.findByUser(managed).isPresent()) {
            throw new ValidationException(msg("error.family.hasPendingRequest", "У вас є активна заявка на вступ до іншої сім'ї."));
        }

        String trimmed = name == null ? "" : name.trim();

        if (trimmed.length() < 2) {
            throw new ValidationException(msg("error.family.nameMinLength", "Мінімум 2 символи"));
        }
        if (trimmed.length() > 20) {
            throw new ValidationException(msg("error.family.nameMaxLength", "Максимум 20 символів"));
        }
        if (!trimmed.matches("^[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9\\s\\-\\_\\'\\.]+$")) {
            throw new ValidationException(msg("error.family.nameInvalidChars", "Недійсні символи"));
        }

        if (familyRepository.existsByNameIgnoreCase(trimmed)) {
            throw new ValidationException(msg("error.family.nameExists", "Сім'я з такою назвою вже існує"));
        }

        Family family = new Family();
        family.setName(trimmed);
        family.setOwner(managed);
        family.setInviteCode(generateUniqueCode());
        Family saved = familyRepository.save(family);

        managed.setFamily(saved);
        userRepository.save(managed);

        log.info("Family '{}' created by {} (code={})",
                saved.getName(), managed.getUsername(), saved.getInviteCode());
        return saved;
    }

    @Transactional
    public void updateFamilyName(User owner, String newName) {
        User managed = userRepository.findById(owner.getId())
                .orElseThrow(() -> new ValidationException(msg("error.user.notFound", "Користувача не знайдено")));
        Family family = managed.getFamily();

        if (family == null) {
            throw new ValidationException(msg("error.family.notInFamily", "Ви не є учасником сім'ї"));
        }
        if (!family.getOwner().getId().equals(managed.getId())) {
            throw new AccessDeniedException(msg("error.family.notOwner", "Тільки власник може змінювати назву"));
        }

        String trimmed = newName == null ? "" : newName.trim();

        if (trimmed.length() < 2) {
            throw new ValidationException(msg("error.family.nameMinLength", "Мінімум 2 символи"));
        }
        if (trimmed.length() > 20) {
            throw new ValidationException(msg("error.family.nameMaxLength", "Максимум 20 символів"));
        }
        if (!trimmed.matches("^[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9\\s\\-\\_\\'\\.]+$")) {
            throw new ValidationException(msg("error.family.nameInvalidChars", "Недійсні символи"));
        }

        if (!family.getName().equalsIgnoreCase(trimmed) && familyRepository.existsByNameIgnoreCase(trimmed)) {
            throw new ValidationException(msg("error.family.nameExists", "Сім'я з такою назвою вже існує"));
        }

        family.setName(trimmed);
        familyRepository.save(family);
        log.info("Family name updated to '{}' by owner {}", trimmed, managed.getUsername());
    }

    @Transactional
    public void joinFamily(User user, String inviteCode) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        if (managed.getFamily() != null) {
            throw new ValidationException("Ви вже є учасником сім'ї");
        }
        if (joinRequestRepository.findByUserAndRejectedFalse(managed).isPresent()) {
            throw new ValidationException("Ви вже маєте активну заявку на вступ до сім'ї. Дочекайтеся підтвердження або відізвіть її.");
        }

        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ValidationException("Введіть код-запрошення");
        }

        Family family = familyRepository.findByInviteCode(code)
                .orElseThrow(() -> new ValidationException("Невірний код-запрошення"));

        // Видаляємо стару відхилену заявку до цієї ж сім'ї, якщо є
        joinRequestRepository.findByFamilyAndUser(family, managed)
                .ifPresent(joinRequestRepository::delete);

        FamilyJoinRequest request = new FamilyJoinRequest();
        request.setFamily(family);
        request.setUser(managed);
        request.setRejected(false);
        joinRequestRepository.save(request);

        log.info("{} requested to join family '{}'", managed.getUsername(), family.getName());
    }

    @Transactional
    public void cancelJoinRequest(User user) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));

        joinRequestRepository.findByUser(managed).ifPresent(request -> {
            joinRequestRepository.delete(request);
            log.info("{} cancelled join request", managed.getUsername());
        });
    }

    @Transactional
    public void leaveFamily(User user) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) {
            throw new ValidationException("Ви не є учасником сім'ї");
        }
        if (family.getOwner().getId().equals(managed.getId())) {
            throw new ValidationException("Власник не може покинути сім'ю.");
        }

        // 👇 Витягуємо власника свіжим з бази 👇
        User owner = userRepository.findById(family.getOwner().getId())
                .orElseThrow(() -> new ValidationException("Власника не знайдено"));
        owner.setNotificationMessage("Учасник " + managed.getUsername() + " покинув вашу сім'ю.");
        userRepository.save(owner);

        expenseRepository.clearSharedFlagForUser(managed);
        incomeRepository.clearSharedFlagForUser(managed);

        managed.setFamily(null);
        userRepository.save(managed);

        // Очищаємо спільні транзакції (якщо ви це вже додали)
        expenseRepository.clearSharedFlagForUser(managed);
        incomeRepository.clearSharedFlagForUser(managed);

        log.info("{} left family '{}'", managed.getUsername(), family.getName());
    }

    @Transactional
    public void removeMember(User owner, Long memberId) {
        User managed = userRepository.findById(owner.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) throw new ValidationException("Ви не є учасником сім'ї");
        if (!family.getOwner().getId().equals(managed.getId()))
            throw new AccessDeniedException("Тільки власник може виконати цю дію");
        if (memberId.equals(managed.getId()))
            throw new ValidationException("Не можна видалити власника");

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ValidationException("Учасника не знайдено"));
        if (member.getFamily() == null
                || !member.getFamily().getId().equals(family.getId()))
            throw new ValidationException("Цей учасник не є членом вашої сім'ї");

        expenseRepository.clearSharedFlagForUser(member);
        incomeRepository.clearSharedFlagForUser(member);

        member.setFamily(null);
        userRepository.save(member);
        expenseRepository.clearSharedFlagForUser(member); // 👈 Очищаємо
        incomeRepository.clearSharedFlagForUser(member);  // 👈 Очищаємо
        userRepository.save(member);
        log.info("Removed {} from family '{}'", member.getUsername(), family.getName());
    }

    @Transactional
    public void deleteFamily(User owner) {
        User managed = userRepository.findById(owner.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) throw new ValidationException("Ви не є учасником сім'ї");
        if (!family.getOwner().getId().equals(managed.getId()))
            throw new AccessDeniedException("Тільки власник може видалити сім'ю");

        List<FamilyJoinRequest> requests = joinRequestRepository.findByFamily(family);
        joinRequestRepository.deleteAll(requests);

        List<User> members = userRepository.findByFamily(family);
        for (User m : members) {
            m.setFamily(null);
            m.setNotificationMessage("Власник видалив сім'ю. Ви більше не є її учасником.");
            expenseRepository.clearSharedFlagForUser(m);
            incomeRepository.clearSharedFlagForUser(m);
        }
        userRepository.saveAll(members);
        familyRepository.delete(family);
        log.info("Family '{}' deleted by owner {}", family.getName(), managed.getUsername());
    }

    @Transactional
    public String regenerateInviteCode(User owner) {
        User managed = userRepository.findById(owner.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) throw new ValidationException("Ви не є учасником сім'ї");
        if (!family.getOwner().getId().equals(managed.getId()))
            throw new AccessDeniedException("Тільки власник може оновити код");
        family.setInviteCode(generateUniqueCode());
        familyRepository.save(family);
        return family.getInviteCode();
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public List<User> getFamilyMembers(User user) {
        Long familyId = user.getFamilyId();
        if (familyId == null) return List.of();
        Family f = familyRepository.findById(familyId).orElse(null);
        if (f == null) return List.of();

        // 👇 Сортуємо: власник завжди перший, інші - за алфавітом 👇
        return userRepository.findByFamily(f).stream()
                .filter(java.util.Objects::nonNull)
                .sorted((u1, u2) -> {
                    boolean isU1Owner = u1.getId().equals(f.getOwner().getId());
                    boolean isU2Owner = u2.getId().equals(f.getOwner().getId());
                    if (isU1Owner) return -1; // u1 йде першим
                    if (isU2Owner) return 1;  // u2 йде першим
                    return u1.getUsername().compareToIgnoreCase(u2.getUsername()); // інші за алфавітом
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Family getFamily(User user) {
        Long familyId = user.getFamilyId();
        if (familyId == null) return null;
        return familyRepository.findById(familyId).orElse(null);
    }

    public boolean isOwner(User user) {
        Long familyId = user.getFamilyId();
        if (familyId == null) return false;
        Family f = familyRepository.findById(familyId).orElse(null);
        return f != null && f.getOwner().getId().equals(user.getId());
    }

    @Transactional(readOnly = true)
    public List<SharedTransactionDto> getSharedTransactions(
            User currentUser, LocalDate start, LocalDate end) {
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) return List.of();

        List<SharedTransactionDto> all = new ArrayList<>();

        // 👇 Оновлені виклики без currentUser.getId() 👇
        all.addAll(expenseRepository.findSharedExpensesByFamily(familyId, start, end));
        all.addAll(incomeRepository.findSharedIncomesByFamily(familyId, start, end));

        all.sort(Comparator.comparing(SharedTransactionDto::date).reversed());
        return all;
    }
    // ==================== JOIN REQUESTS ====================

    @Transactional(readOnly = true)
    public List<FamilyJoinRequest> getPendingRequests(User owner) {
        Family family = getFamily(owner);
        if (family == null || !isOwner(owner)) return List.of();
        return joinRequestRepository.findByFamilyAndRejectedFalse(family);
    }

    @Transactional(readOnly = true)
    public FamilyJoinRequest getUserPendingRequest(User user) {
        if (user.getFamilyId() != null) return null;
        return joinRequestRepository.findByUserAndRejectedFalse(user).orElse(null);
    }

    @Transactional(readOnly = true)
    public FamilyJoinRequest getUserRejectedRequest(User user) {
        if (user.getFamilyId() != null) return null;
        return joinRequestRepository.findByUserAndRejectedTrue(user).orElse(null);
    }

    @Transactional
    public void clearRejectedRequest(User user) {
        joinRequestRepository.findByUserAndRejectedTrue(user)
                .ifPresent(joinRequestRepository::delete);
    }

    @Transactional
    public void approveRequest(User owner, Long requestId) {
        User managedOwner = userRepository.findById(owner.getId()).orElseThrow();
        Family family = managedOwner.getFamily();
        if (family == null || !family.getOwner().getId().equals(managedOwner.getId())) {
            throw new AccessDeniedException("Тільки власник може підтверджувати запити");
        }

        FamilyJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ValidationException("Запит не знайдено"));

        if (!request.getFamily().getId().equals(family.getId())) {
            throw new AccessDeniedException("Цей запит належить іншій сім'ї");
        }

        User newMember = request.getUser();
        newMember.setFamily(family);
        userRepository.save(newMember);
        joinRequestRepository.delete(request);
        log.info("Owner {} approved {} to join family '{}'", managedOwner.getUsername(), newMember.getUsername(), family.getName());
    }

    @Transactional
    public void rejectRequest(User owner, Long requestId) {
        User managedOwner = userRepository.findById(owner.getId()).orElseThrow();
        Family family = managedOwner.getFamily();
        if (family == null || !family.getOwner().getId().equals(managedOwner.getId())) {
            throw new AccessDeniedException("Тільки власник може відхиляти запити");
        }

        FamilyJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new ValidationException("Запит не знайдено"));

        if (!request.getFamily().getId().equals(family.getId())) {
            throw new AccessDeniedException("Цей запит належить іншій сім'ї");
        }

        request.setRejected(true);
        joinRequestRepository.save(request);
        log.info("Owner {} rejected join request", managedOwner.getUsername());
    }

    // ==================== HELPERS ====================

    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = generateCode();
            if (familyRepository.findByInviteCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Cannot generate unique invite code");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++)
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private String msg(String key, String fallback) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Transactional
    public void transferOwnership(User currentOwner, Long newOwnerId) {
        User managedOwner = userRepository.findById(currentOwner.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managedOwner.getFamily();

        if (family == null) {
            throw new ValidationException("Ви не є учасником сім'ї");
        }
        if (!family.getOwner().getId().equals(managedOwner.getId())) {
            throw new AccessDeniedException("Тільки власник може передати права");
        }

        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new ValidationException("Нового власника не знайдено"));

        if (newOwner.getFamily() == null || !newOwner.getFamily().getId().equals(family.getId())) {
            throw new ValidationException("Цей користувач не є членом вашої сім'ї");
        }
        if (newOwner.getId().equals(managedOwner.getId())) {
            throw new ValidationException("Ви вже є власником");
        }

        family.setOwner(newOwner);
        familyRepository.save(family);
        log.info("Ownership of family '{}' transferred from {} to {}",
                family.getName(), managedOwner.getUsername(), newOwner.getUsername());
    }
}