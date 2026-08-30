// NoteService.java
package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    @Transactional
    public void deleteAllNotes(User user) {
        List<Note> notes = noteRepository.findByOwner(user);
        if (!notes.isEmpty()) {
            noteRepository.deleteAll(notes);
        }
    }

    @Transactional
    public Note createNote(Note note) {
        return noteRepository.save(note);
    }

    @Transactional
    public Note updateNote(Note note) {
        return noteRepository.save(note);
    }

    @Transactional
    public void deleteNote(Long id) {
        noteRepository.deleteById(id);
    }

    @Transactional
    public Note togglePinned(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new RuntimeException("Note not found"));
        note.setPinned(!note.isPinned());
        return noteRepository.save(note);
    }

    @Transactional
    public Note toggleArchived(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new RuntimeException("Note not found"));
        note.setArchived(!note.isArchived());
        return noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public List<Note> getActiveNotes(User user) {
        if (user == null) {
            return List.of();
        }
        return noteRepository.findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Note> getArchivedNotes(User user) {
        if (user == null) {
            return List.of();
        }
        return noteRepository.findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Note> searchNotes(User user, String query) {
        if (user == null) {
            return List.of();
        }
        return noteRepository.searchNotes(user, query);
    }

    @Transactional(readOnly = true)
    public Note getNoteById(Long id, User user) {
        if (user == null) {
            throw new RuntimeException("Користувач не знайдений");
        }
        Note note = noteRepository.findById(id).orElseThrow(() -> new RuntimeException("Note not found"));
        if (!note.getOwner().equals(user)) {
            throw new RuntimeException("Access denied");
        }
        return note;
    }
}