package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.service.FamilyService;
import org.lazycent.service.NoteExportService;
import org.lazycent.service.NoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController extends BaseController {

    private final NoteService noteService;
    private final FamilyService familyService;
    private final NoteExportService noteExportService;
    private static final int FAMILY_NOTES_PAGE_SIZE = 20;

    @GetMapping("/family")
    public String viewFamilyNotes(Authentication authentication,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        if (currentUser.getFamilyId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", msg("error.family.notInFamily"));
            return "redirect:/family";
        }

        Family family = familyService.getFamily(currentUser);

        Page<Note> page0 = noteService.getFamilySharedNotesPaged(
                currentUser, "", "", "all", false,
                0, FAMILY_NOTES_PAGE_SIZE, resolveFamilySort("default"));

        long[] badges = noteService.countFamilySharedBadges(currentUser, "", "", false);

        model.addAttribute("family", family);
        model.addAttribute("familyNotes", page0.getContent());
        model.addAttribute("familyNotesTotal", page0.getTotalElements());
        model.addAttribute("familyNotesHasMore", page0.hasNext());
        model.addAttribute("familyNotesBadgeAll", badges[0]);
        model.addAttribute("familyNotesBadgeMine", badges[1]);
        model.addAttribute("currentUser", currentUser);
        return "family/notes";
    }

    private static Sort resolveFamilySort(String sortKey) {
        if (sortKey == null || sortKey.isBlank()) {
            return Sort.by("pinned").descending().and(Sort.by("updatedAt").descending());
        }
        return switch (sortKey) {
            case "title"   -> Sort.by("title").ascending();
            case "author"  -> Sort.by("owner.username").ascending();
            case "created" -> Sort.by("createdAt").descending();
            case "updated" -> Sort.by("updatedAt").descending();
            default        -> Sort.by("pinned").descending().and(Sort.by("updatedAt").descending());
        };
    }

    // ==================== СТОРІНКА ====================

    @GetMapping
    public String viewNotes(@RequestParam(value = "q", required = false) String q,
                            @RequestParam(value = "sort", required = false, defaultValue = "default") String sort,
                            @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                            @RequestParam(value = "create", required = false, defaultValue = "false") boolean create,
                            @RequestParam(value = "filter", required = false, defaultValue = "all") String filter,
                            Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        String query = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

        Sort activeSort = NoteService.resolveSort(sort, false);
        Sort archivedSort = NoteService.resolveSort(sort, true);

        boolean showActive = "all".equals(filter) || "active".equals(filter) || "shared".equals(filter);
        boolean showArchived = "all".equals(filter) || "archived".equals(filter) || "shared".equals(filter);

        Page<Note> activePage = Page.empty();
        Page<Note> archivedPage = Page.empty();

        if (showActive) {
            if ("shared".equals(filter)) {
                activePage = (query == null)
                        ? noteService.getSharedNotesPaged(currentUser, false, 0, size, activeSort)
                        : noteService.searchSharedNotesPaged(currentUser, false, query, 0, size, activeSort);
            } else {
                activePage = (query == null)
                        ? noteService.getActiveNotes(currentUser, 0, size, activeSort)
                        : noteService.searchActiveNotes(currentUser, query, 0, size, activeSort);
            }
        }
        if (showArchived) {
            if ("shared".equals(filter)) {
                archivedPage = (query == null)
                        ? noteService.getSharedNotesPaged(currentUser, true, 0, size, archivedSort)
                        : noteService.searchSharedNotesPaged(currentUser, true, query, 0, size, archivedSort);
            } else {
                archivedPage = (query == null)
                        ? noteService.getArchivedNotes(currentUser, 0, size, archivedSort)
                        : noteService.searchArchivedNotes(currentUser, query, 0, size, archivedSort);
            }
        }

        long activeTotal = noteService.countActiveNotes(currentUser);
        long archivedTotal = noteService.countArchivedNotes(currentUser);
        long sharedTotal = noteService.countSharedNotes(currentUser);

        model.addAttribute("notes", activePage.getContent());
        model.addAttribute("archivedNotes", archivedPage.getContent());
        model.addAttribute("activeTotal", activeTotal);
        model.addAttribute("archivedTotal", archivedTotal);
        model.addAttribute("sharedTotal", sharedTotal);
        model.addAttribute("activeHasMore", activePage.hasNext());
        model.addAttribute("archivedHasMore", archivedPage.hasNext());
        model.addAttribute("activeFilteredTotal", activePage.getTotalElements());
        model.addAttribute("archivedFilteredTotal", archivedPage.getTotalElements());
        model.addAttribute("q", query);
        model.addAttribute("sort", sort);
        model.addAttribute("size", size);
        model.addAttribute("filter", filter);
        model.addAttribute("openCreateModal", create);

        return "notes";
    }

    // ==================== CRUD ====================

    @PostMapping("/create")
    public String createNote(@RequestParam String title,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String color,
                             @RequestParam(required = false) String textColor,
                             @RequestParam(required = false, defaultValue = "false") Boolean shareWithFamily,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            Note note = new Note();
            note.setTitle(title);
            note.setContent(content != null ? content : "");
            note.setColor(color != null ? color : "#ffffff");
            note.setTextColor(textColor != null ? textColor : "#212529");
            note.setOwner(currentUser);
            note.setShareWithFamily(shareWithFamily);
            noteService.createNote(note);

            redirectAttributes.addFlashAttribute("successMessage", msg("toast.note.created"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notes";
    }

    @PostMapping("/update/{id}")
    public String updateNote(@PathVariable Long id,
                             @RequestParam String title,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String color,
                             @RequestParam(required = false, defaultValue = "false") Boolean shareWithFamily,
                             @RequestParam(required = false) String textColor,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            noteService.updateNote(id, title, content, color, textColor, shareWithFamily, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.note.updated"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notes";
    }

    @PostMapping("/delete/{id}")
    public String deleteNote(@PathVariable Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        noteService.deleteNote(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", msg("toast.note.deleted"));
        return "redirect:/notes";
    }

    @PostMapping("/toggle-pin/{id}")
    public String togglePin(@PathVariable Long id,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        Note result = noteService.togglePinned(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage",
                result.isPinned() ? msg("toast.note.pinned") : msg("toast.note.unpinned"));
        return "redirect:/notes";
    }

    @PostMapping("/toggle-archive/{id}")
    public String toggleArchive(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        Note result = noteService.toggleArchived(id, currentUser);
        redirectAttributes.addFlashAttribute("successMessage",
                result.isArchived() ? msg("toast.note.archived") : msg("toast.note.unarchived"));
        return "redirect:/notes";
    }

    @PostMapping("/archive/clear")
    public String clearArchive(Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            int deleted = noteService.deleteArchivedNotes(currentUser);
            if (deleted > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        msg("toast.archive.cleared", deleted));
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        msg("toast.archive.alreadyEmpty"));
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notes";
    }

    // ==================== ДУБЛЮВАННЯ ====================

    @PostMapping("/{id}/duplicate")
    public String duplicateNote(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            Note copy = noteService.duplicateNote(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Нотатку скопійовано: " + copy.getTitle());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notes";
    }

    // ==================== BULK ====================

    @PostMapping("/bulk/archive")
    @ResponseBody
    public Map<String, Object> bulkArchive(@RequestParam("ids") List<Long> ids,
                                           Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return Map.of("success", false, "error", "Unauthorized");
        }
        try {
            int count = noteService.bulkArchive(ids, currentUser);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/bulk/delete")
    @ResponseBody
    public Map<String, Object> bulkDelete(@RequestParam("ids") List<Long> ids,
                                          Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return Map.of("success", false, "error", "Unauthorized");
        }
        try {
            int count = noteService.bulkDelete(ids, currentUser);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/bulk/unarchive")
    @ResponseBody
    public Map<String, Object> bulkUnarchive(@RequestParam("ids") List<Long> ids,
                                             Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return Map.of("success", false, "error", "Unauthorized");
        }
        try {
            int count = noteService.bulkUnarchive(ids, currentUser);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ==================== COPY (сім'я) ====================

    @PostMapping("/copy/{id}")
    public String copyNote(@PathVariable Long id,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        try {
            noteService.copyNote(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", msg("toast.note.copied"));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notes";
    }

    @GetMapping("/export")
    public void exportNotes(
            @RequestParam(value = "sort", required = false, defaultValue = "default") String sort,
            @RequestParam(value = "filter", required = false, defaultValue = "all") String filter,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "member", required = false) String member,
            Authentication authentication,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            response.sendRedirect("/login");
            return;
        }

        byte[] xlsx = noteExportService.export(
                currentUser, sort, filter, q, member,
                org.springframework.context.i18n.LocaleContextHolder.getLocale());

        String filename = "notes-" + filter + "-"
                + java.time.LocalDate.now() + ".xlsx";
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
    }

    @PostMapping("/bulk/copy")
    @ResponseBody
    public Map<String, Object> bulkCopy(@RequestParam("ids") List<Long> ids,
                                        Authentication authentication) {
        User user = getCurrentUser(authentication);
        if (user == null) {
            return Map.of("success", false, "error", "Unauthorized");
        }
        try {
            int count = noteService.bulkCopyShared(ids, user);
            return Map.of("success", true, "count", count);
        } catch (Exception e) {
            return Map.of("success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "error");
        }
    }
}