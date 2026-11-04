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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    /** Максимальні довжини — мають збігатися з колонками БД (V1). */
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 5000;

    /** Скільки нотаток показувати за один "завантажити ще". */
    public static final int PAGE_SIZE = 20;

    private final NoteRepository noteRepository;

    // ==================== ВАЛІДАЦІЯ ====================

    /**
     * Обрізає пробіли та перевіряє довжину/порожнечу title.
     *
     * <p>Дублює клієнтську валідацію навмисно: curl/Postman обходять `maxlength`,
     * і без серверної перевірки в БД можна вставити 10 МБ title → падіння колонки.
     */
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

    /**
     * Обрізає пробіли в content та перевіряє довжину.
     * {@code null} → {@code ""} (порожня нотатка валідна).
     */
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
        // страховка: якщо хтось створив Note без перевірки — обрізаємо тут
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

    // ==================== ЧИТАННЯ (PAGINATED) ====================

    @Transactional(readOnly = true)
    public Page<Note> getActiveNotes(User user, int page) {
        return getActiveNotes(user, page, PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Page<Note> getActiveNotes(User user, int page, int size) {
        if (user == null) return Page.empty();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return noteRepository.findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(user, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Note> getArchivedNotes(User user, int page) {
        return getArchivedNotes(user, page, PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Page<Note> getArchivedNotes(User user, int page, int size) {
        if (user == null) return Page.empty();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return noteRepository.findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(user, pageable);
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

    @Transactional(readOnly = true)
    public Page<Note> searchActiveNotes(User user, String query, int page) {
        return searchActiveNotes(user, query, page, PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Page<Note> searchActiveNotes(User user, String query, int page, int size) {
        if (user == null || query == null || query.trim().isEmpty()) {
            return getActiveNotes(user, page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return noteRepository.searchActive(user, query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Note> searchArchivedNotes(User user, String query, int page) {
        return searchArchivedNotes(user, query, page, PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public Page<Note> searchArchivedNotes(User user, String query, int page, int size) {
        if (user == null || query == null || query.trim().isEmpty()) {
            return getArchivedNotes(user, page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return noteRepository.searchArchived(user, query.trim(), pageable);
    }
}