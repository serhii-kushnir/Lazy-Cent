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

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("test");
    }

    // ---------- helpers ----------

    private Note note(Long id, User owner) {
        Note n = new Note();
        n.setId(id);
        n.setOwner(owner);
        n.setTitle("Title");
        n.setContent("Content");
        n.setColor("#ffffff");
        n.setTextColor("#212529");
        n.setShareWithFamily(false);
        n.setPinned(false);
        n.setArchived(false);
        return n;
    }

    // ---------- updateNote ----------

    @Test
    void updateNote_changesFields_forOwner() {
        Note existing = note(10L, testUser);
        when(noteRepository.findByIdAndOwner(10L, testUser)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(
                10L, "Нова назва", "Новий текст",
                "#f28b82", "#ffffff", true, testUser);

        assertThat(result.getTitle()).isEqualTo("Нова назва");
        assertThat(result.getContent()).isEqualTo("Новий текст");
        assertThat(result.getColor()).isEqualTo("#f28b82");
        assertThat(result.getTextColor()).isEqualTo("#ffffff");
        assertThat(result.getShareWithFamily()).isTrue();

        verify(noteRepository).save(existing);
    }

    @Test
    void updateNote_throwsAccessDenied_forIntruder() {
        User intruder = new User();
        intruder.setId(999L);
        intruder.setUsername("intruder");

        when(noteRepository.findByIdAndOwner(10L, intruder)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                noteService.updateNote(10L, "X", "Y", "#000000", "#ffffff", false, intruder))
                .isInstanceOf(AccessDeniedException.class);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void updateNote_keepsExistingColors_whenNull() {
        Note existing = note(10L, testUser);
        existing.setColor("#ccff90");
        existing.setTextColor("#000000");
        when(noteRepository.findByIdAndOwner(10L, testUser)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(
                10L, "T", "C", null, null, false, testUser);

        assertThat(result.getColor()).isEqualTo("#ccff90");
        assertThat(result.getTextColor()).isEqualTo("#000000");
    }

    @Test
    void updateNote_handlesNullContent() {
        Note existing = note(10L, testUser);
        when(noteRepository.findByIdAndOwner(10L, testUser)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(
                10L, "T", null, "#ffffff", "#212529", false, testUser);

        assertThat(result.getContent()).isEqualTo("");
    }

    @Test
    void updateNote_keepsExistingShare_whenNull() {
        Note existing = note(10L, testUser);
        existing.setShareWithFamily(true);
        when(noteRepository.findByIdAndOwner(10L, testUser)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(
                10L, "T", "C", "#ffffff", "#212529", null, testUser);

        assertThat(result.getShareWithFamily()).isTrue();
    }

    // ---------- createNote ----------

    @Test
    void createNote_savesNote() {
        Note n = note(null, testUser);
        when(noteRepository.save(any())).thenReturn(n);

        Note result = noteService.createNote(n);

        assertThat(result).isSameAs(n);
        verify(noteRepository).save(n);
    }
}