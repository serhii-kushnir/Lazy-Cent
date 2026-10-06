package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.repository.NoteRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @InjectMocks
    private NoteService noteService;

    private User owner;
    private User intruder;
    private Note note;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        intruder = new User();
        intruder.setId(2L);
        intruder.setUsername("intruder");

        note = new Note();
        note.setId(100L);
        note.setTitle("Test note");
        note.setContent("Content");
        note.setOwner(owner);
    }

    // ==================== ЗАХИСТ ВІД IDOR ====================

    @Test
    void deleteNote_succeedsForOwner() {
        when(noteRepository.findByIdAndOwner(100L, owner)).thenReturn(Optional.of(note));

        noteService.deleteNote(100L, owner);

        verify(noteRepository).delete(note);
    }

    @Test
    void deleteNote_throwsAccessDenied_forIntruder() {
        when(noteRepository.findByIdAndOwner(100L, intruder)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.deleteNote(100L, intruder))
                .isInstanceOf(AccessDeniedException.class);

        verify(noteRepository, never()).delete(any());
    }

    @Test
    void togglePinned_throwsAccessDenied_forIntruder() {
        when(noteRepository.findByIdAndOwner(100L, intruder)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.togglePinned(100L, intruder))
                .isInstanceOf(AccessDeniedException.class);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void toggleArchived_throwsAccessDenied_forIntruder() {
        when(noteRepository.findByIdAndOwner(100L, intruder)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.toggleArchived(100L, intruder))
                .isInstanceOf(AccessDeniedException.class);

        verify(noteRepository, never()).save(any());
    }

    // ==================== БІЗНЕС-ЛОГІКА ====================

    @Test
    void togglePinned_flipsBoolean() {
        note.setPinned(false);
        when(noteRepository.findByIdAndOwner(100L, owner)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.togglePinned(100L, owner);

        assertThat(result.isPinned()).isTrue();
    }

    @Test
    void toggleArchived_flipsBoolean() {
        note.setArchived(false);
        when(noteRepository.findByIdAndOwner(100L, owner)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.toggleArchived(100L, owner);

        assertThat(result.isArchived()).isTrue();
    }

    @Test
    void updateNote_changesFields_forOwner() {
        when(noteRepository.findByIdAndOwner(100L, owner)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note updated = noteService.updateNote(100L, "New title", "New content",
                "#fff", "#000", owner);

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getContent()).isEqualTo("New content");
        assertThat(updated.getColor()).isEqualTo("#fff");
        assertThat(updated.getTextColor()).isEqualTo("#000");
    }

    @Test
    void updateNote_throwsAccessDenied_forIntruder() {
        when(noteRepository.findByIdAndOwner(100L, intruder)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.updateNote(100L, "x", "y",
                null, null, intruder))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== CREATE ====================

    @Test
    void createNote_savesAndReturnsNote() {
        when(noteRepository.save(any(Note.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Note input = new Note();
        input.setTitle("Test");
        input.setOwner(owner);

        Note result = noteService.createNote(input);

        assertThat(result).isSameAs(input);
        verify(noteRepository).save(input);
    }

    // ==================== READ ====================

    @Test
    void getArchivedNotes_returnsEmpty_forNullUser() {
        assertThat(noteService.getArchivedNotes(null)).isEmpty();
    }

    @Test
    void getArchivedNotes_delegatesToRepository() {
        when(noteRepository.findByOwnerAndArchivedTrueOrderByUpdatedAtDesc(owner))
                .thenReturn(java.util.List.of(note));

        assertThat(noteService.getArchivedNotes(owner)).containsExactly(note);
    }

    @Test
    void searchNotes_returnsEmpty_forNullUser() {
        assertThat(noteService.searchNotes(null, "query")).isEmpty();
    }

    @Test
    void searchNotes_delegatesToRepository() {
        when(noteRepository.searchNotes(owner, "test"))
                .thenReturn(java.util.List.of(note));

        assertThat(noteService.searchNotes(owner, "test")).containsExactly(note);
    }

    @Test
    void getNoteById_returnsForOwner() {
        when(noteRepository.findByIdAndOwner(100L, owner))
                .thenReturn(java.util.Optional.of(note));

        assertThat(noteService.getNoteById(100L, owner)).isSameAs(note);
    }

    @Test
    void getNoteById_throwsAccessDenied_forIntruder() {
        when(noteRepository.findByIdAndOwner(100L, intruder))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> noteService.getNoteById(100L, intruder))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== DELETE ALL ====================

    @Test
    void deleteAllNotes_deletesNothing_whenEmpty() {
        when(noteRepository.findByOwner(owner)).thenReturn(java.util.List.of());

        noteService.deleteAllNotes(owner);

        verify(noteRepository, never()).deleteAll(anyList());
    }

    @Test
    void deleteAllNotes_deletesAll_whenNonEmpty() {
        when(noteRepository.findByOwner(owner)).thenReturn(java.util.List.of(note));

        noteService.deleteAllNotes(owner);

        verify(noteRepository).deleteAll(java.util.List.of(note));
    }

    // ==================== UPDATE WITH NULLS ====================

    @Test
    void updateNote_keepsExistingColors_whenNull() {
        note.setColor("#abc");
        note.setTextColor("#def");
        when(noteRepository.findByIdAndOwner(100L, owner))
                .thenReturn(java.util.Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(100L, "t", "c", null, null, owner);

        assertThat(result.getColor()).isEqualTo("#abc");
        assertThat(result.getTextColor()).isEqualTo("#def");
    }

    @Test
    void updateNote_handlesNullContent() {
        when(noteRepository.findByIdAndOwner(100L, owner))
                .thenReturn(java.util.Optional.of(note));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(100L, "t", null, null, null, owner);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getActiveNotes_returnsEmpty_forNullUser() {
        assertThat(noteService.getActiveNotes(null)).isEmpty();
    }
}