package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.service.NoteService;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;
import java.util.Set;

/**
 * AJAX-контролер для нотаток.
 *
 * <p><b>Чому @RestController, а не @Controller:</b> методи повертають
 * готовий HTML-рядок (результат {@code templateEngine.process(...)}),
 * а не ім'я view. З {@code @Controller} Spring MVC сприймав би цей рядок
 * як ім'я view і намагався б його відрендерити через
 * {@code ThymeleafViewResolver} — що і викликало помилку
 * {@code Error resolving template []}.
 *
 * <p><b>Чому {@code Set<String> selectors}, а не {@code ":: fragment"}:</b>
 * перегрузка {@code process(String, IContext)} не завжди коректно
 * розбирає {@code ::} на ім'я + селектор. Явна передача селектора
 * через {@code Set<String>} — це задокументований і надійний API.
 */
@RestController
@RequestMapping("/notes/api")
@RequiredArgsConstructor
@Slf4j
public class NoteApiController extends BaseController {

    private final NoteService noteService;
    private final SpringTemplateEngine templateEngine;

    private static final String NOTE_CARD_TEMPLATE = "fragments/note-card";
    private static final String NOTE_GRID_TEMPLATE = "fragments/note-grid";

    private static final Set<String> ACTIVE_CARD_SELECTOR   = Set.of("activeCard");
    private static final Set<String> ARCHIVED_CARD_SELECTOR = Set.of("archivedCard");
    private static final Set<String> FULL_GRID_SELECTOR     = Set.of("fullGrid");

    // ==================== LOAD MORE ====================

    @GetMapping(value = "/load-more", produces = MediaType.TEXT_HTML_VALUE)
    public String loadMore(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "active") String type,
                           @RequestParam(required = false) String q,
                           Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "";

        try {
            String query = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

            Page<Note> result;
            Set<String> selectors;

            if ("archived".equals(type)) {
                result = (query == null)
                        ? noteService.getArchivedNotes(currentUser, page)
                        : noteService.searchArchivedNotes(currentUser, query, page);
                selectors = ARCHIVED_CARD_SELECTOR;
            } else {
                result = (query == null)
                        ? noteService.getActiveNotes(currentUser, page)
                        : noteService.searchActiveNotes(currentUser, query, page);
                selectors = ACTIVE_CARD_SELECTOR;
            }

            Context ctx = new Context();
            List<Note> notes = result.getContent();

            StringBuilder html = new StringBuilder();
            for (Note note : notes) {
                ctx.setVariable("note", note);
                // ✅ фікс: template + selector окремо, а не "template :: fragment"
                html.append(templateEngine.process(NOTE_CARD_TEMPLATE, selectors, ctx));
            }
            return html.toString();

        } catch (Exception e) {
            log.error("loadMore failed for type='{}', q='{}': {}", type, q, e.getMessage(), e);
            return "";   // з @RestController → 200 OK з порожнім тілом
        }
    }

    // ==================== SEARCH ====================

    @GetMapping(value = "/search", produces = MediaType.TEXT_HTML_VALUE)
    public String search(@RequestParam(required = false) String q,
                         Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "";

        try {
            String query = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

            Page<Note> activePage = (query == null)
                    ? noteService.getActiveNotes(currentUser, 0)
                    : noteService.searchActiveNotes(currentUser, query, 0);

            Page<Note> archivedPage = (query == null)
                    ? noteService.getArchivedNotes(currentUser, 0)
                    : noteService.searchArchivedNotes(currentUser, query, 0);

            Context ctx = new Context();
            ctx.setVariable("notes", activePage.getContent());
            ctx.setVariable("archivedNotes", archivedPage.getContent());
            ctx.setVariable("activeHasMore", activePage.hasNext());
            ctx.setVariable("archivedHasMore", archivedPage.hasNext());
            ctx.setVariable("q", query);

            // ✅ фікс: template + selector окремо, а не "template :: fullGrid"
            return templateEngine.process(NOTE_GRID_TEMPLATE, FULL_GRID_SELECTOR, ctx);

        } catch (Exception e) {
            log.error("Search rendering failed for q='{}': {}", q, e.getMessage(), e);
            return "";
        }
    }
}