package org.lazycent.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.service.NoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication; // <-- ДОДАНО ІМПОРТ
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // ==================== FAMILY SHARED — PAGED (AJAX) ====================

    private static final String FAMILY_NOTES_CARDS_TEMPLATE = "fragments/family-note-cards";

    @GetMapping(value = "/family", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> familyNotesPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "all") String owner,
            @RequestParam(defaultValue = "false") boolean pinned,
            @RequestParam(defaultValue = "default") String sort,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        User currentUser = getCurrentUser(authentication);
        if (currentUser == null || currentUser.getFamilyId() == null) {
            return ResponseEntity.ok(Map.of(
                    "html", "", "hasMore", false, "total", 0L,
                    "page", 0, "badgeAll", 0L, "badgeMine", 0L));
        }

        Page<Note> pageResult = noteService.getFamilySharedNotesPaged(
                currentUser, q, author, owner, pinned, page, size,
                resolveFamilySortForApi(sort));

        long[] badges = noteService.countFamilySharedBadges(currentUser, q, author, pinned);

        String html = "";
        if (!pageResult.isEmpty()) {
            try {
                var application = JakartaServletWebApplication.buildApplication(request.getServletContext());
                var exchange = application.buildExchange(request, response);
                WebContext ctx = new WebContext(exchange,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale());
                ctx.setVariable("familyNotes", pageResult.getContent());
                ctx.setVariable("currentUser", currentUser);
                html = templateEngine.process(FAMILY_NOTES_CARDS_TEMPLATE, ctx);
            } catch (Exception e) {
                log.error("family notes fragment render failed: {}", e.getMessage(), e);
                return ResponseEntity.ok(Map.of(
                        "html", "", "hasMore", false, "total", 0L,
                        "page", page, "badgeAll", badges[0], "badgeMine", badges[1]));
            }
        }

        return ResponseEntity.ok(Map.of(
                "html", html,
                "hasMore", pageResult.hasNext(),
                "total", pageResult.getTotalElements(),
                "page", page,
                "badgeAll", badges[0],
                "badgeMine", badges[1]));
    }

    private static org.springframework.data.domain.Sort resolveFamilySortForApi(String sortKey) {
        if (sortKey == null || sortKey.isBlank()) {
            return org.springframework.data.domain.Sort.by("pinned").descending()
                    .and(org.springframework.data.domain.Sort.by("updatedAt").descending());
        }
        return switch (sortKey) {
            case "title"   -> org.springframework.data.domain.Sort.by("title").ascending();
            case "author"  -> org.springframework.data.domain.Sort.by("owner.username").ascending();
            case "created" -> org.springframework.data.domain.Sort.by("createdAt").descending();
            case "updated" -> org.springframework.data.domain.Sort.by("updatedAt").descending();
            default        -> org.springframework.data.domain.Sort.by("pinned").descending()
                    .and(org.springframework.data.domain.Sort.by("updatedAt").descending());
        };
    }

    // ==================== LOAD MORE ====================

    @GetMapping(value = "/load-more", produces = MediaType.TEXT_HTML_VALUE)
    public String loadMore(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "active") String type,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "default") String sort,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "";

        try {
            String query = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
            boolean archived = "archived".equals(type);
            Sort sortSpec = NoteService.resolveSort(sort, archived);

            Page<Note> result;
            Set<String> selectors;

            if (archived) {
                result = (query == null)
                        ? noteService.getArchivedNotes(currentUser, page, size, sortSpec)
                        : noteService.searchArchivedNotes(currentUser, query, page, size, sortSpec);
                selectors = ARCHIVED_CARD_SELECTOR;
            } else {
                result = (query == null)
                        ? noteService.getActiveNotes(currentUser, page, size, sortSpec)
                        : noteService.searchActiveNotes(currentUser, query, page, size, sortSpec);
                selectors = ACTIVE_CARD_SELECTOR;
            }

            // ✅ FIX: створюємо IWebExchange, а потім WebContext
            var application = JakartaServletWebApplication.buildApplication(request.getServletContext());
            var exchange = application.buildExchange(request, response);
            WebContext webCtx = new WebContext(exchange, org.springframework.context.i18n.LocaleContextHolder.getLocale());

            List<Note> notes = result.getContent();
            StringBuilder html = new StringBuilder();
            for (Note note : notes) {
                webCtx.setVariable("note", note);
                html.append(templateEngine.process(NOTE_CARD_TEMPLATE, selectors, webCtx));
            }
            return html.toString();

        } catch (Exception e) {
            log.error("loadMore failed for type='{}', q='{}': {}", type, q, e.getMessage(), e);
            return "";
        }
    }

    // ==================== SEARCH ====================

    @GetMapping(value = "/search", produces = MediaType.TEXT_HTML_VALUE)
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "20") int size,
                         @RequestParam(defaultValue = "default") String sort,
                         Authentication authentication,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "";

        try {
            String query = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

            Sort activeSort = NoteService.resolveSort(sort, false);
            Sort archivedSort = NoteService.resolveSort(sort, true);

            Page<Note> activePage = (query == null)
                    ? noteService.getActiveNotes(currentUser, 0, size, activeSort)
                    : noteService.searchActiveNotes(currentUser, query, 0, size, activeSort);

            Page<Note> archivedPage = (query == null)
                    ? noteService.getArchivedNotes(currentUser, 0, size, archivedSort)
                    : noteService.searchArchivedNotes(currentUser, query, 0, size, archivedSort);

            // ✅ FIX: створюємо IWebExchange, а потім WebContext
            var application = JakartaServletWebApplication.buildApplication(request.getServletContext());
            var exchange = application.buildExchange(request, response);
            WebContext webCtx = new WebContext(exchange, org.springframework.context.i18n.LocaleContextHolder.getLocale());

            webCtx.setVariable("notes", activePage.getContent());
            webCtx.setVariable("archivedNotes", archivedPage.getContent());
            webCtx.setVariable("activeHasMore", activePage.hasNext());
            webCtx.setVariable("archivedHasMore", archivedPage.hasNext());
            webCtx.setVariable("q", query);

            return templateEngine.process(NOTE_GRID_TEMPLATE, FULL_GRID_SELECTOR, webCtx);

        } catch (Exception e) {
            log.error("Search rendering failed for q='{}': {}", q, e.getMessage(), e);
            return "";
        }
    }
}