package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Family;
import org.lazycent.model.FamilyJoinRequest;
import org.lazycent.model.User;
import org.lazycent.model.dto.SharedDayDto;
import org.lazycent.model.dto.SharedMemberSummaryDto;
import org.lazycent.model.dto.SharedTransactionDto;
import org.lazycent.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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

        return userRepository.findByFamily(f).stream()
                .filter(java.util.Objects::nonNull)
                .sorted((u1, u2) -> {
                    boolean isU1Owner = u1.getId().equals(f.getOwner().getId());
                    boolean isU2Owner = u2.getId().equals(f.getOwner().getId());
                    if (isU1Owner) return -1;
                    if (isU2Owner) return 1;
                    return u1.getUsername().compareToIgnoreCase(u2.getUsername());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Family getFamily(User user) {
        Long familyId = user.getFamilyId();
        if (familyId == null) return null;
        return familyRepository.findByIdWithOwner(familyId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(User user) {
        Long familyId = user.getFamilyId();
        if (familyId == null) return false;
        return familyRepository.findById(familyId)
                .map(f -> f.getOwner().getId().equals(user.getId()))
                .orElse(false);
    }

    // ==================== SHARED TRANSACTIONS ====================

    /**
     * Повертає всі спільні транзакції сім'ї за період.
     *
     * <p><b>Сортування (P0):</b> date DESC → time DESC → id DESC.
     * Стабільний порядок — критично для коректного обчислення
     * running balance на клієнті (інакше баланс «стрибає» між запитами).
     */
    @Transactional(readOnly = true)
    public List<SharedTransactionDto> getSharedTransactions(
            User currentUser, LocalDate start, LocalDate end) {

        Long familyId = currentUser.getFamilyId();
        if (familyId == null) return List.of();

        List<SharedTransactionDto> all = new ArrayList<>();
        all.addAll(expenseRepository.findSharedExpensesByFamily(familyId, start, end));
        all.addAll(incomeRepository.findSharedIncomesByFamily(familyId, start, end));

        Comparator<SharedTransactionDto> cmp = Comparator
                .comparing(SharedTransactionDto::date,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SharedTransactionDto::time,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SharedTransactionDto::id,
                        Comparator.nullsLast(Comparator.reverseOrder()));

        all.sort(cmp);
        return all;
    }

    /** Транзакції за конкретний день (для /family/shared/day/{date}). */
    @Transactional(readOnly = true)
    public List<SharedTransactionDto> getSharedTransactionsByDate(User currentUser, LocalDate date) {
        return getSharedTransactions(currentUser, date, date);
    }

    /**
     * Список днів зі спільними транзакціями + підсумки.
     *
     * <p>Використовує SQL-агрегацію (SUM/COUNT по днях) — не тягне
     * 1000+ транзакцій у пам'ять, лише 30-60 днів.
     */
    @Transactional(readOnly = true)
    public List<SharedDayDto> getSharedDays(User currentUser, LocalDate start, LocalDate end) {
        List<SharedDayDto> summaries = getSharedDaySummaries(currentUser, start, end);
        if (summaries.isEmpty()) return summaries;

        List<SharedTransactionDto> transactions = getSharedTransactions(currentUser, start, end);
        Map<LocalDate, List<SharedTransactionDto>> transactionsByDate = transactions.stream()
                .collect(Collectors.groupingBy(SharedTransactionDto::date));
        return summaries.stream()
                .map(day -> withTransactions(day, transactionsByDate.getOrDefault(day.date(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SharedDayDto> getSharedDaysPaged(User currentUser, LocalDate start, LocalDate end,
                                                 int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        List<SharedDayDto> summaries = getSharedDaySummaries(currentUser, start, end);
        safePage = Math.min(safePage, Math.max(0, (summaries.size() - 1) / safeSize));
        int fromIndex = Math.min(safePage * safeSize, summaries.size());
        int toIndex = Math.min(fromIndex + safeSize, summaries.size());
        List<SharedDayDto> selected = summaries.subList(fromIndex, toIndex);
        if (selected.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(safePage, safeSize), summaries.size());
        }

        LocalDate newest = selected.get(0).date();
        LocalDate oldest = selected.get(selected.size() - 1).date();
        List<SharedTransactionDto> transactions = getSharedTransactions(currentUser, oldest, newest);
        Map<LocalDate, List<SharedTransactionDto>> transactionsByDate = transactions.stream()
                .collect(Collectors.groupingBy(SharedTransactionDto::date));
        List<SharedDayDto> pageContent = selected.stream()
                .map(day -> withTransactions(day, transactionsByDate.getOrDefault(day.date(), List.of())))
                .toList();

        return new PageImpl<>(pageContent, PageRequest.of(safePage, safeSize), summaries.size());
    }

    @Transactional(readOnly = true)
    public List<SharedMemberSummaryDto> getSharedMemberSummaries(User currentUser, LocalDate start, LocalDate end) {
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) return List.of();

        Map<String, double[]> totals = new HashMap<>();
        for (Object[] row : expenseRepository.sumSharedExpensesByMember(familyId, start, end)) {
            totals.computeIfAbsent((String) row[0], key -> new double[2])[0] = ((Number) row[1]).doubleValue();
        }
        for (Object[] row : incomeRepository.sumSharedIncomesByMember(familyId, start, end)) {
            totals.computeIfAbsent((String) row[0], key -> new double[2])[1] = ((Number) row[1]).doubleValue();
        }
        return totals.entrySet().stream()
                .map(entry -> new SharedMemberSummaryDto(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[1] - entry.getValue()[0]))
                .sorted(Comparator.comparing(SharedMemberSummaryDto::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SharedDayDto> getSharedDaySummaries(User currentUser, LocalDate start, LocalDate end) {
        Long familyId = currentUser.getFamilyId();
        if (familyId == null) return List.of();

        Map<LocalDate, double[]> stats = new TreeMap<>(Comparator.reverseOrder());

        for (Object[] r : expenseRepository.sumSharedExpensesByDay(familyId, start, end)) {
            LocalDate d = (LocalDate) r[0];
            stats.computeIfAbsent(d, k -> new double[2]);
            stats.get(d)[0] += ((Number) r[1]).doubleValue();
        }
        for (Object[] r : incomeRepository.sumSharedIncomesByDay(familyId, start, end)) {
            LocalDate d = (LocalDate) r[0];
            stats.computeIfAbsent(d, k -> new double[2]);
            stats.get(d)[1] += ((Number) r[1]).doubleValue();
        }

        List<SharedDayDto> result = new ArrayList<>(stats.size());
        for (var entry : stats.entrySet()) {
            LocalDate date = entry.getKey();
            result.add(new SharedDayDto(
                    date, getDayName(date),
                    entry.getValue()[0], entry.getValue()[1],
                    entry.getValue()[1] - entry.getValue()[0],
                    List.of()));
        }
        return result;
    }

    private SharedDayDto withTransactions(SharedDayDto summary, List<SharedTransactionDto> transactions) {
        return new SharedDayDto(summary.date(), summary.dayName(), summary.totalExpenses(),
                summary.totalIncomes(), summary.balance(), transactions);
    }

    public String getDayName(LocalDate date) {
        if (date == null) return "";
        return switch (date.getDayOfWeek()) {
            case MONDAY -> msg("day.monday", "Понеділок");
            case TUESDAY -> msg("day.tuesday", "Вівторок");
            case WEDNESDAY -> msg("day.wednesday", "Середа");
            case THURSDAY -> msg("day.thursday", "Четвер");
            case FRIDAY -> msg("day.friday", "П'ятниця");
            case SATURDAY -> msg("day.saturday", "Субота");
            case SUNDAY -> msg("day.sunday", "Неділя");
        };
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
    public void updateFamilyName(User owner, String newName) {
        User managed = userRepository.findById(owner.getId())
                .orElseThrow(() -> new ValidationException(msg("error.user.notFound", "Користувача не знайдено")));
        Family family = managed.getFamily();

        if (family == null) throw new ValidationException(msg("error.family.notInFamily", "Ви не є учасником сім'ї"));
        if (!family.getOwner().getId().equals(managed.getId())) throw new AccessDeniedException(msg("error.family.notOwner", "Тільки власник може змінювати назву"));

        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.length() < 2) throw new ValidationException(msg("error.family.nameMinLength", "Мінімум 2 символи"));
        if (trimmed.length() > 20) throw new ValidationException(msg("error.family.nameMaxLength", "Максимум 20 символів"));
        if (!trimmed.matches("^[a-zA-Zа-яА-ЯіІїЇєЄґҐ0-9\\s\\-\\_\\'\\.]+$")) throw new ValidationException(msg("error.family.nameInvalidChars", "Недійсні символи"));
        if (!family.getName().equalsIgnoreCase(trimmed) && familyRepository.existsByNameIgnoreCase(trimmed)) {
            throw new ValidationException(msg("error.family.nameExists", "Сім'я з такою назвою вже існує"));
        }

        family.setName(trimmed);
        familyRepository.save(family);

        List<User> members = userRepository.findByFamily(family);
        for (User m : members) {
            if (!m.getId().equals(managed.getId())) {
                m.setNotificationMessage("Власник змінив назву сім'ї на '" + trimmed + "'.");
            }
        }
        userRepository.saveAll(members);
        log.info("Family name updated to '{}' by owner {}", trimmed, managed.getUsername());
    }

    @Transactional
    public void removeMember(User owner, Long memberId) {
        User managed = userRepository.findById(owner.getId()).orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) throw new ValidationException("Ви не є учасником сім'ї");
        if (!family.getOwner().getId().equals(managed.getId())) throw new AccessDeniedException("Тільки власник може виконати цю дію");
        if (memberId.equals(managed.getId())) throw new ValidationException("Не можна видалити власника");

        User member = userRepository.findById(memberId).orElseThrow(() -> new ValidationException("Учасника не знайдено"));
        if (member.getFamily() == null || !member.getFamily().getId().equals(family.getId()))
            throw new ValidationException("Цей учасник не є членом вашої сім'ї");

        expenseRepository.clearSharedFlagForUser(member);
        incomeRepository.clearSharedFlagForUser(member);

        member.setFamily(null);
        member.setNotificationMessage("Власник сім'ї видалив вас зі списку учасників.");
        userRepository.save(member);

        List<User> remainingMembers = userRepository.findByFamily(family);
        for (User m : remainingMembers) {
            m.setNotificationMessage("Учасника " + member.getUsername() + " було видалено з сім'ї.");
        }
        userRepository.saveAll(remainingMembers);

        log.info("Removed {} from family '{}'", member.getUsername(), family.getName());
    }

    @Transactional
    public void leaveFamily(User user) {
        User managed = userRepository.findById(user.getId()).orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managed.getFamily();
        if (family == null) throw new ValidationException("Ви не є учасником сім'ї");
        if (family.getOwner().getId().equals(managed.getId())) throw new ValidationException("Власник не може покинути сім'ю.");

        expenseRepository.clearSharedFlagForUser(managed);
        incomeRepository.clearSharedFlagForUser(managed);

        managed.setFamily(null);
        userRepository.save(managed);

        List<User> remainingMembers = userRepository.findByFamily(family);
        for (User m : remainingMembers) {
            m.setNotificationMessage("Учасник " + managed.getUsername() + " покинув вашу сім'ю.");
        }
        userRepository.saveAll(remainingMembers);

        log.info("{} left family '{}'", managed.getUsername(), family.getName());
    }

    @Transactional
    public void transferOwnership(User currentOwner, Long newOwnerId) {
        User managedOwner = userRepository.findById(currentOwner.getId())
                .orElseThrow(() -> new ValidationException("Користувача не знайдено"));
        Family family = managedOwner.getFamily();

        if (family == null) throw new ValidationException("Ви не є учасником сім'ї");
        if (!family.getOwner().getId().equals(managedOwner.getId())) throw new AccessDeniedException("Тільки власник може передати права");

        User newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new ValidationException("Нового власника не знайдено"));

        if (newOwner.getFamily() == null || !newOwner.getFamily().getId().equals(family.getId())) {
            throw new ValidationException("Цей користувач не є членом вашої сім'ї");
        }
        if (newOwner.getId().equals(managedOwner.getId())) throw new ValidationException("Ви вже є власником");

        family.setOwner(newOwner);
        familyRepository.save(family);

        List<User> members = userRepository.findByFamily(family);
        for (User m : members) {
            if (m.getId().equals(managedOwner.getId())) {
                m.setNotificationMessage("Ви передали права власника учаснику " + newOwner.getUsername() + ".");
            } else if (m.getId().equals(newOwner.getId())) {
                m.setNotificationMessage("Вам передано права власника сім'ї.");
            } else {
                m.setNotificationMessage("Учасник " + newOwner.getUsername() + " тепер є новим власником сім'ї.");
            }
        }
        userRepository.saveAll(members);

        log.info("Ownership of family '{}' transferred from {} to {}", family.getName(), managedOwner.getUsername(), newOwner.getUsername());
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

        List<User> members = userRepository.findByFamily(family);
        for (User m : members) {
            if (!m.getId().equals(newMember.getId())) {
                m.setNotificationMessage("До сім'ї приєднався новий учасник: " + newMember.getUsername() + ".");
            }
        }
        userRepository.saveAll(members);

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
}