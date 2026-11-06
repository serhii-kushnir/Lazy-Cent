package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.repository.NoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NoteService {

    /** Скільки нотаток показувати за один "завантажити ще" за замовчуванням. */
    public static final int PAGE_SIZE = 20;

    /** Максимальні довжини — мають збігатися з колонками БД (V1). */
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 5000;

    private final NoteRepository noteRepository;

    // ==================== SORT HELPERS ====================

    /**
     * Повертає {@link Sort} для вказаного режиму.
     *
     * <p><b>Для активних нотаток</b> ({@code archived = false}) закріплені
     * завжди йдуть першими, незалежно від вибраного сортування — це
     * узгоджено з Google Keep.
     *
     * @param sortKey одне з: {@code default}, {@code title}, {@code created},
     *                {@code updated}, {@code color}
     * @param archived чи це сортування для архіву
     */
    public static Sort resolveSort(String sortKey, boolean archived) {
        Sort base = switch (sortKey == null ? "default" : sortKey) {
            case "title"   -> Sort.by("title").ascending();
            case "created" -> Sort.by("createdAt").descending();
            case "updated" -> Sort.by("updatedAt").descending();
            case "color"   -> Sort.by("color").ascending();
            default        -> Sort.by("updatedAt").descending();
        };
        if (!archived) {
            base = Sort.by("pinned").descending().and(base);
        }
        return base;
    }

    /**
     * Будує {@link Pageable} з підтримкою {@code size = 0} («Всі»).
     * {@code Integer.MAX_VALUE} як LIMIT повертає всі рядки за один запит.
     */
    public static Pageable buildPageable(int page, int size, Sort sort) {
        int pageSize = (size <= 0) ? Integer.MAX_VALUE : Math.max(1, size);
        return PageRequest.of(Math.max(0, page), pageSize, sort);
    }

    // ==================== ВАЛІДАЦІЯ ====================

    private String validateAndTrimTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Назва нотатки не може бути порожньою");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(
                    "Назва нотатки не може перевищувати " + MAX_TITLE_LENGTH + " символів");
        }
        return trimmed;
    }

    private String validateAndTrimContent(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new ValidationException(
                    "Текст нотатки не може перевищувати " + MAX_CONTENT_LENGTH + " символів");
        }
        return trimmed;
    }

    // ==================== ПЕРЕВІРКА ДОСТУПУ ====================

    private Note getOwnedNote(Long id, User user) {
        if (user == null) {
            throw new AccessDeniedException("Користувач не автентифікований");
        }
        return noteRepository.findByIdAndOwner(id, user)
                .orElseThrow(() -> new AccessDeniedException(
                        "Нотатку не знайдено або вона не належить вам"));
    }

    // ==================== СТВОРЕННЯ / ОНОВЛЕННЯ ====================

    @Transactional
    public Note createNote(Note note) {
        note.setTitle(validateAndTrimTitle(note.getTitle()));
        note.setContent(validateAndTrimContent(note.getContent()));
        return noteRepository.save(note);
    }

    @Transactional
    public Note updateNote(Long id, String title, String content,
                           String color, String textColor,
                           Boolean shareWithFamily, User user) {
        Note note = getOwnedNote(id, user);

        note.setTitle(validateAndTrimTitle(title));
        note.setContent(validateAndTrimContent(content));

        if (color != null && !color.isEmpty()) {
            note.setColor(color);
        }
        if (textColor != null && !textColor.isEmpty()) {
            note.setTextColor(textColor);
        }
        if (shareWithFamily != null) {
            note.setShareWithFamily(shareWithFamily);
        }

        return noteRepository.save(note);
    }

    // ==================== ВИДАЛЕННЯ ====================

    @Transactional
    public void deleteNote(Long id, User user) {
        Note note = getOwnedNote(id, user);
        noteRepository.delete(note);
    }

    @Transactional
    public void deleteAllNotes(User user) {
        List<Note> notes = noteRepository.findByOwner(user);
        if (!notes.isEmpty()) {
            noteRepository.deleteAll(notes);
            noteRepository.flush();
        }
    }

    @Transactional
    public int deleteArchivedNotes(User user) {
        if (user == null) return 0;
        return noteRepository.deleteArchivedByOwner(user);
    }

    // ==================== ДУБЛЮВАННЯ ====================

    /**
     * Створює копію нотатки. Копія — приватна (не shared), активна,
     * без закріплення. Назва — з суфіксом «(копія)».
     */
    @Transactional
    public Note duplicateNote(Long sourceId, User user) {
        Note source = getOwnedNote(sourceId, user);
        Note copy = new Note();
        copy.setTitle(source.getTitle() + " (копія)");
        copy.setContent(source.getContent());
        copy.setColor(source.getColor());
        copy.setTextColor(source.getTextColor());
        copy.setOwner(user);
        copy.setShareWithFamily(false);
        copy.setArchived(false);
        copy.setPinned(false);
        return noteRepository.save(copy);
    }

    // ==================== BULK ====================

    /**
     * Архівує всі вказані нотатки, що належать користувачу.
     *
     * <p><b>Захист від IDOR:</b> {@code findAllById(ids)} може повернути
     * чужі нотатки, якщо передати чужі id. Тому фільтруємо по
     * {@code owner.id}. Зайві id — просто ігноруються.
     */
    @Transactional
    public int bulkArchive(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty() || user == null) return 0;

        List<Note> notes = noteRepository.findAllById(ids).stream()
                .filter(n -> Objects.equals(n.getOwner().getId(), user.getId()))
                .filter(n -> !n.isArchived())
                .toList();

        if (notes.isEmpty()) return 0;
        notes.forEach(n -> n.setArchived(true));
        noteRepository.saveAll(notes);
        return notes.size();
    }

    @Transactional
    public int bulkDelete(List<Long> ids, User user) {
        if (ids == null || ids.isEmpty() || user == null) return 0;

        List<Note> notes = noteRepository.findAllById(ids).stream()
                .filter(n -> Objects.equals(n.getOwner().getId(), user.getId()))
                .toList();

        if (notes.isEmpty()) return 0;
        noteRepository.deleteAll(notes);
        return notes.size();
    }

    // ==================== ЗАКРІПЛЕННЯ / АРХІВ ====================

    @Transactional
    public Note togglePinned(Long id, User user) {
        Note note = getOwnedNote(id, user);
        note.setPinned(!note.isPinned());
        return noteRepository.save(note);
    }

    @Transactional
    public Note toggleArchived(Long id, User user) {
        Note note = getOwnedNote(id, user);
        note.setArchived(!note.isArchived());
        return noteRepository.save(note);
    }

    // ==================== ЧИТАННЯ ====================

    @Transactional(readOnly = true)
    public Page<Note> getActiveNotes(User user, int page) {
        return getActiveNotes(user, page, PAGE_SIZE, resolveSort("default", false));
    }

    @Transactional(readOnly = true)
    public Page<Note> getActiveNotes(User user, int page, int size) {
        return getActiveNotes(user, page, size, resolveSort("default", false));
    }

    @Transactional(readOnly = true)
    public Page<Note> getActiveNotes(User user, int page, int size, Sort sort) {
        if (user == null) return Page.empty();
        Pageable pageable = buildPageable(page, size, sort);
        return noteRepository.findByOwnerAndArchivedFalse(user, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Note> getArchivedNotes(User user, int page) {
        return getArchivedNotes(user, page, PAGE_SIZE, resolveSort("default", true));
    }

    @Transactional(readOnly = true)
    public Page<Note> getArchivedNotes(User user, int page, int size) {
        return getArchivedNotes(user, page, size, resolveSort("default", true));
    }

    @Transactional(readOnly = true)
    public Page<Note> getArchivedNotes(User user, int page, int size, Sort sort) {
        if (user == null) return Page.empty();
        Pageable pageable = buildPageable(page, size, sort);
        return noteRepository.findByOwnerAndArchivedTrue(user, pageable);
    }

    // ==================== ПОШУК ====================

    @Transactional(readOnly = true)
    public List<Note> searchNotes(User user, String query) {
        if (user == null) return List.of();
        return noteRepository.searchNotes(user, query);
    }

    @Transactional(readOnly = true)
    public Note getNoteById(Long id, User user) {
        return getOwnedNote(id, user);
    }

    @Transactional(readOnly = true)
    public Page<Note> searchActiveNotes(User user, String query, int page) {
        return searchActiveNotes(user, query, page, PAGE_SIZE, resolveSort("default", false));
    }

    @Transactional(readOnly = true)
    public Page<Note> searchActiveNotes(User user, String query, int page, int size) {
        return searchActiveNotes(user, query, page, size, resolveSort("default", false));
    }

    @Transactional(readOnly = true)
    public Page<Note> searchActiveNotes(User user, String query, int page, int size, Sort sort) {
        if (user == null || query == null || query.trim().isEmpty()) {
            return getActiveNotes(user, page, size, sort);
        }
        Pageable pageable = buildPageable(page, size, sort);
        return noteRepository.searchActive(user, query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Note> searchArchivedNotes(User user, String query, int page) {
        return searchArchivedNotes(user, query, page, PAGE_SIZE, resolveSort("default", true));
    }

    @Transactional(readOnly = true)
    public Page<Note> searchArchivedNotes(User user, String query, int page, int size) {
        return searchArchivedNotes(user, query, page, size, resolveSort("default", true));
    }

    @Transactional(readOnly = true)
    public Page<Note> searchArchivedNotes(User user, String query, int page, int size, Sort sort) {
        if (user == null || query == null || query.trim().isEmpty()) {
            return getArchivedNotes(user, page, size, sort);
        }
        Pageable pageable = buildPageable(page, size, sort);
        return noteRepository.searchArchived(user, query.trim(), pageable);
    }

    // ==================== FAMILY SHARING ====================

    @Transactional(readOnly = true)
    public List<Note> getFamilySharedNotes(User user) {
        if (user == null || user.getFamilyId() == null) return List.of();
        return noteRepository.findSharedWithFamily(user.getFamilyId());
    }

    @Transactional
    public Note copyNote(Long sourceId, User user) {
        Note source = noteRepository.findByIdWithOwner(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Нотатку", sourceId));

        if (source.getOwner().getFamilyId() == null
                || !source.getOwner().getFamilyId().equals(user.getFamilyId())) {
            throw new AccessDeniedException("Нотатка не з вашої сім'ї");
        }
        if (!Boolean.TRUE.equals(source.getShareWithFamily())) {
            throw new AccessDeniedException("Нотатка не є спільною");
        }

        Note copy = new Note();
        copy.setTitle(source.getTitle() + " (копія)");
        copy.setContent(source.getContent());
        copy.setColor(source.getColor());
        copy.setTextColor(source.getTextColor());
        copy.setOwner(user);
        copy.setShareWithFamily(false);
        copy.setArchived(false);
        copy.setPinned(false);
        return noteRepository.save(copy);
    }

    // ==================== COUNTS ====================

    @Transactional(readOnly = true)
    public long countActiveNotes(User user) {
        if (user == null) return 0;
        return noteRepository.countByOwnerAndArchivedFalse(user);
    }

    @Transactional(readOnly = true)
    public long countArchivedNotes(User user) {
        if (user == null) return 0;
        return noteRepository.countByOwnerAndArchivedTrue(user);
    }

    @Transactional(readOnly = true)
    public long countSharedNotes(User user) {
        if (user == null) return 0;
        return noteRepository.countByOwnerAndShareWithFamilyTrue(user);
    }
}