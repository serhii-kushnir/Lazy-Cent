package org.lazycent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.FamilyService;
import org.lazycent.service.NoteService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteControllerTest {

    @Mock
    private NoteService noteService;

    @Mock
    private FamilyService familyService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private NoteController controller;

    private User testUser;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        // BaseController має @Autowired-поля, які @InjectMocks не заповнює — ставимо явно
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "messageSource", messageSource);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("test");

        auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(testUser);

        // повідомлення-заглушки
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- updateNote ----------

    @Test
    void updateNote_redirectsWithSuccess() {
        RedirectAttributes ra = mock(RedirectAttributes.class);
        when(noteService.updateNote(anyLong(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new Note());

        String view = controller.updateNote(
                10L,
                "Нова назва",
                "Новий текст",
                "#f28b82",
                true,
                "#ffffff",
                auth,
                ra);

        // ВАЖЛИВО: порядок у verify МАЄ збігатися з сигнатурою NoteService.updateNote:
        // (id, title, content, color, textColor, shareWithFamily, user)
        verify(noteService).updateNote(
                eq(10L),
                eq("Нова назва"),
                eq("Новий текст"),
                eq("#f28b82"),
                eq("#ffffff"),
                eq(true),
                eq(testUser));

        assertThat(view).isEqualTo("redirect:/notes");
    }

    // ---------- createNote ----------

    @Test
    void createNote_setsShareWithFamily_andRedirects() {
        RedirectAttributes ra = mock(RedirectAttributes.class);

        String view = controller.createNote(
                "Title", "Content", "#ffffff", "#212529", true, auth, ra);

        verify(noteService).createNote(argThat(n ->
                n.getTitle().equals("Title")
                        && n.getContent().equals("Content")
                        && n.getShareWithFamily().equals(true)
                        && n.getOwner().getId().equals(testUser.getId())
        ));
        assertThat(view).isEqualTo("redirect:/notes");
    }
}