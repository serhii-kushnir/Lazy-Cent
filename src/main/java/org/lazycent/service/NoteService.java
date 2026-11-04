package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.repository.NoteRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

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
        return noteRepository.save(note);
    }

    /**
     * Оновлює нотатку.
     *
     * <p><b>Сигнатура (порядок аргументів):</b>
     * {@code (id, title, content, color, textColor, shareWithFamily, user)}
     *
     * <p><b>Поведінка з null:</b>
     * <ul>
     *   <li>{@code content == null} → зберігається порожній рядок</li>
     *   <li>{@code color == null / empty} → залишається старий</li>
     *   <li>{@code textColor == null / empty} → залишається старий</li>
     *   <li>{@code shareWithFamily == null} → залишається старий</li>
     * </ul>
     */
    @Transactional
    public Note updateNote(Long id, String title, String content,
                           String color, String textColor,
                           Boolean shareWithFamily, User user) {
        Note note = getOwnedNote(id, user);

        note.setTitle(title);
        note.setContent(content != null ? content : "");

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

    // ==================== ЧИТАННЯ ====================

    @Transactional(readOnly = true)
    public List<Note> getActiveNotes(User user) {
        if (user == null) return List.of();
        return noteRepository.findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Note> getArchivedNotes(User user) {
        if (user == null) return List.of();
        return noteRepository.findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(user);
    }

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

        // має бути з тієї ж сім'ї
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
}