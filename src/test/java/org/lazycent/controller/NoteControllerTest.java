package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.NoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private NoteService noteService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("note_ctrl_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    /**
     * Кладе українську локаль у сесію для конкретного запиту.
     *
     * <p><b>Навіщо:</b> {@code SessionLocaleResolver} читає локаль ТІЛЬКИ
     * з сесії під ключем {@link SessionLocaleResolver#LOCALE_SESSION_ATTRIBUTE_NAME}.
     * {@code LocaleContextHolder.setLocale(...)} у {@code @BeforeEach}
     * перезаписується {@code DispatcherServlet} під час запиту.
     *
     * <p>Використовується у тестах, де очікується український текст
     * (регресійний захист від хардкоду — до P0 fix повідомлення були
     * хардкоджені українською, і ці тести проходили випадково).
     */
    private MockHttpServletRequestBuilder withUkrainianLocale(
            MockHttpServletRequestBuilder builder) {
        return builder
                .sessionAttr(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME,
                        Locale.forLanguageTag("uk-UA"))
                .locale(Locale.forLanguageTag("uk-UA"));
    }

    // ==================== GET /notes ====================

    @Test
    void viewNotes_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(get("/notes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void viewNotes_rendersForAuthenticated() throws Exception {
        when(noteService.getActiveNotes(testUser)).thenReturn(List.of());
        when(noteService.getArchivedNotes(testUser)).thenReturn(List.of());

        mockMvc.perform(get("/notes").with(user(testUser)))
                .andExpect(status().isOk())
                .andExpect(view().name("notes"))
                .andExpect(model().attributeExists("notes"))
                .andExpect(model().attributeExists("archivedNotes"));
    }

    // ==================== POST /notes/create ====================

    @Test
    void createNote_savesAndRedirects() throws Exception {
        mockMvc.perform(post("/notes/create")
                        .with(csrf())
                        .with(user(testUser))
                        .param("title", "Моя нотатка")
                        .param("content", "Текст")
                        .param("color", "#f28b82")
                        .param("textColor", "#212529"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(noteService).createNote(any(Note.class));
    }

    @Test
    void createNote_usesDefaults_whenColorAndTextColorMissing() throws Exception {
        mockMvc.perform(post("/notes/create")
                        .with(csrf())
                        .with(user(testUser))
                        .param("title", "Заголовок"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"));

        verify(noteService).createNote(argThat(note ->
                note.getTitle().equals("Заголовок")
                        && "#ffffff".equals(note.getColor())
                        && "#212529".equals(note.getTextColor())
                        && testUser.equals(note.getOwner())));
    }

    @Test
    void createNote_redirectsToLogin_forAnonymous() throws Exception {
        mockMvc.perform(post("/notes/create")
                        .with(csrf())
                        .param("title", "X"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ==================== POST /notes/update/{id} ====================

    @Test
    void updateNote_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/notes/update/10")
                        .with(csrf())
                        .with(user(testUser))
                        .param("title", "Нова назва")
                        .param("content", "Новий текст"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(noteService).updateNote(eq(10L), eq("Нова назва"), eq("Новий текст"),
                any(), any(), eq(testUser));
    }

    // ==================== POST /notes/delete/{id} ====================

    @Test
    void deleteNote_redirectsWithSuccess() throws Exception {
        mockMvc.perform(post("/notes/delete/10")
                        .with(csrf())
                        .with(user(testUser)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(noteService).deleteNote(10L, testUser);
    }

    // ==================== POST /notes/toggle-pin/{id} ====================
    //
    // FIX: тести очікують український текст → треба .sessionAttr(...)
    // з uk-UA, бо NoteController тепер використовує msg() (P0 fix).

    @Test
    void togglePin_pinned_returnsPinnedMessage() throws Exception {
        Note note = new Note();
        note.setId(10L);
        note.setPinned(true);

        when(noteService.togglePinned(10L, testUser)).thenReturn(note);

        mockMvc.perform(withUkrainianLocale(post("/notes/toggle-pin/10")
                        .with(csrf())
                        .with(user(testUser))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        org.hamcrest.Matchers.containsString("закріплено")));
    }

    @Test
    void togglePin_unpinned_returnsUnpinnedMessage() throws Exception {
        Note note = new Note();
        note.setId(10L);
        note.setPinned(false);

        when(noteService.togglePinned(10L, testUser)).thenReturn(note);

        mockMvc.perform(withUkrainianLocale(post("/notes/toggle-pin/10")
                        .with(csrf())
                        .with(user(testUser))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        org.hamcrest.Matchers.containsString("відкріплено")));
    }

    // ==================== POST /notes/toggle-archive/{id} ====================

    @Test
    void toggleArchive_archived_returnsArchivedMessage() throws Exception {
        Note note = new Note();
        note.setId(10L);
        note.setArchived(true);

        when(noteService.toggleArchived(10L, testUser)).thenReturn(note);

        mockMvc.perform(withUkrainianLocale(post("/notes/toggle-archive/10")
                        .with(csrf())
                        .with(user(testUser))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        org.hamcrest.Matchers.containsString("заархівовано")));
    }

    @Test
    void toggleArchive_unarchived_returnsRestoredMessage() throws Exception {
        Note note = new Note();
        note.setId(10L);
        note.setArchived(false);

        when(noteService.toggleArchived(10L, testUser)).thenReturn(note);

        mockMvc.perform(withUkrainianLocale(post("/notes/toggle-archive/10")
                        .with(csrf())
                        .with(user(testUser))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        org.hamcrest.Matchers.containsString("відновлено")));
    }
}