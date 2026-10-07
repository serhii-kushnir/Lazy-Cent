package org.lazycent.controller;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Перевіряє локалізацію повідомлень у {@link NoteController}.
 *
 * <p><b>Чому .sessionAttr, а не LocaleContextHolder:</b>
 * {@link SessionLocaleResolver} читає локаль ТІЛЬКИ з сесії під ключем
 * {@link SessionLocaleResolver#LOCALE_SESSION_ATTRIBUTE_NAME}.
 * {@code LocaleContextHolder.setLocale(...)} у {@code @BeforeEach}
 * перезаписується {@code DispatcherServlet} під час запиту.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteControllerLocalizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @MockBean private NoteService noteService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("note_loc_test_user");
        testUser.setPassword("x");
        testUser.setRole("USER");
        testUser.setPreferredCurrency("UAH");
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private MockHttpServletRequestBuilder withLocale(
            MockHttpServletRequestBuilder builder, Locale locale) {
        return builder
                .sessionAttr(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, locale)
                .locale(locale);
    }

    // ==================== create ====================

    @Test
    void createNote_messageIsEnglish_whenLocaleEn() throws Exception {
        mockMvc.perform(withLocale(post("/notes/create")
                                .with(csrf())
                                .with(user(testUser))
                                .param("title", "Test"),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("created")))
                .andExpect(flash().attribute("successMessage",
                        not(containsString("створено"))));
    }

    @Test
    void createNote_messageIsUkrainian_whenLocaleUk() throws Exception {
        mockMvc.perform(withLocale(post("/notes/create")
                                .with(csrf())
                                .with(user(testUser))
                                .param("title", "Тест"),
                        Locale.forLanguageTag("uk-UA")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("створено")));
    }

    // ==================== update ====================

    @Test
    void updateNote_messageIsEnglish_whenLocaleEn() throws Exception {
        mockMvc.perform(withLocale(post("/notes/update/10")
                                .with(csrf())
                                .with(user(testUser))
                                .param("title", "New"),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("updated")))
                .andExpect(flash().attribute("successMessage",
                        not(containsString("оновлено"))));
    }

    // ==================== delete ====================

    @Test
    void deleteNote_messageIsEnglish_whenLocaleEn() throws Exception {
        mockMvc.perform(withLocale(post("/notes/delete/10")
                                .with(csrf())
                                .with(user(testUser)),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("deleted")))
                .andExpect(flash().attribute("successMessage",
                        not(containsString("видалено"))));
    }

    // ==================== toggle-pin ====================

    @Test
    void togglePin_pinned_messageIsEnglish_whenLocaleEn() throws Exception {
        Note pinned = new Note();
        pinned.setId(10L);
        pinned.setPinned(true);
        when(noteService.togglePinned(eq(10L), any())).thenReturn(pinned);

        mockMvc.perform(withLocale(post("/notes/toggle-pin/10")
                                .with(csrf())
                                .with(user(testUser)),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("pinned")))
                .andExpect(flash().attribute("successMessage",
                        not(containsString("закріплено"))));
    }

    @Test
    void togglePin_unpinned_messageIsUkrainian_whenLocaleUk() throws Exception {
        Note unpinned = new Note();
        unpinned.setId(10L);
        unpinned.setPinned(false);
        when(noteService.togglePinned(eq(10L), any())).thenReturn(unpinned);

        mockMvc.perform(withLocale(post("/notes/toggle-pin/10")
                                .with(csrf())
                                .with(user(testUser)),
                        Locale.forLanguageTag("uk-UA")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("відкріплено")));
    }

    // ==================== toggle-archive ====================

    @Test
    void toggleArchive_archived_messageIsEnglish_whenLocaleEn() throws Exception {
        Note archived = new Note();
        archived.setId(10L);
        archived.setArchived(true);
        when(noteService.toggleArchived(eq(10L), any())).thenReturn(archived);

        mockMvc.perform(withLocale(post("/notes/toggle-archive/10")
                                .with(csrf())
                                .with(user(testUser)),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("archived")))
                .andExpect(flash().attribute("successMessage",
                        not(containsString("заархівовано"))));
    }

    @Test
    void toggleArchive_unarchived_messageIsEnglish_whenLocaleEn() throws Exception {
        Note unarchived = new Note();
        unarchived.setId(10L);
        unarchived.setArchived(false);
        when(noteService.toggleArchived(eq(10L), any())).thenReturn(unarchived);

        mockMvc.perform(withLocale(post("/notes/toggle-archive/10")
                                .with(csrf())
                                .with(user(testUser)),
                        Locale.ENGLISH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("restored")));
    }
}