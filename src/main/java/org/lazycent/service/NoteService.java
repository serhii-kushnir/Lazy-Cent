// service/NoteService.java
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
        return noteRepository.findByOwnerAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Note> getArchivedNotes(User user) {
        return noteRepository.findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Note> searchNotes(User user, String query) {
        return noteRepository.searchNotes(user, query);
    }

    @Transactional(readOnly = true)
    public List<Note> searchNotes(User user, boolean archived, String query) {
        return noteRepository.searchNotes(user, archived, query);
    }

    @Transactional(readOnly = true)
    public Note getNoteById(Long id, User user) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new RuntimeException("Note not found"));
        if (!note.getOwner().equals(user)) {
            throw new RuntimeException("Access denied");
        }
        return note;
    }
}