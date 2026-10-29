package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Family;
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
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final MessageSource messageSource;

    // ==================== CREATE / JOIN / LEAVE ====================

    @Transactional
    public Family createFamily(User user, String name) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        if (managed.getFamily() != null) {
            throw new ValidationException(msg("error.family.alreadyInFamily", "Ви вже є учасником сім'ї"));
        }
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(msg("error.family.nameEmpty", "Назва сім'ї не може бути порожньою"));
        }
        if (trimmed.length() > 100) {
            throw new ValidationException("Назва сім'ї не може перевищувати 100 символів");
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
    public Family joinFamily(User user, String inviteCode) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        if (managed.getFamily() != null) {
            throw new ValidationException(msg("error.family.alreadyInFamily", "Ви вже є учасником сім'ї"));
        }
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ValidationException(msg("error.family.inviteCodeEmpty", "Введіть код-запрошення"));
        }
        Family family = familyRepository.findByInviteCode(code)
                .orElseThrow(() -> new ValidationException(msg("error.family.inviteCodeInvalid", "Невірний код-запрошення")));

        managed.setFamily(family);
        userRepository.save(managed);
        log.info("{} joined family '{}'", managed.getUsername(), family.getName());
        return family;
    }

    @Transactional
    public void leaveFamily(User user) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) {
            throw new ValidationException(msg("error.family.notInFamily", "Ви не є учасником сім'ї"));
        }
        if (family.getOwner().getId().equals(managed.getId())) {
            throw new ValidationException(msg("error.family.ownerCannotLeave", "Власник не може покинути сім'ю"));
        }
        managed.setFamily(null);
        userRepository.save(managed);
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

        member.setFamily(null);
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

        List<User> members = userRepository.findByFamily(family);
        for (User m : members) m.setFamily(null);
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
        return userRepository.findByFamily(f);
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
        all.addAll(expenseRepository.findSharedExpensesByFamily(
                familyId, currentUser.getId(), start, end));
        all.addAll(incomeRepository.findSharedIncomesByFamily(
                familyId, currentUser.getId(), start, end));
        all.sort(Comparator.comparing(SharedTransactionDto::date).reversed());
        return all;
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
}