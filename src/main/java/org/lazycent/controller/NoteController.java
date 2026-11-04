package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Family;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.service.FamilyService;
import org.lazycent.service.NoteService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController extends BaseController {

    private final NoteService noteService;
    private final FamilyService familyService;

    // ==================== СТОРІНКИ ====================

    @GetMapping
    public String viewNotes(@RequestParam(value = "q", required = false) String q,
                            Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) return "redirect:/login";

        String query = (q != null && !q.trim().isEmpty()) ? q.trim() : null;

        Page<Note> activePage = (query == null)
                ? noteService.getActiveNotes(currentUser, 0)
                : noteService.searchActiveNotes(currentUser, query, 0);

        Page<Note> archivedPage = (query == null)
                ? noteService.getArchivedNotes(currentUser, 0)
                : noteService.searchArchivedNotes(currentUser, query, 0);

        model.addAttribute("notes", activePage.getContent());
        model.addAttribute("archivedNotes", archivedPage.getContent());
        model.addAttribute("activeTotal", activePage.getTotalElements());
        model.addAttribute("archivedTotal", archivedPage.getTotalElements());
        model.addAttribute("activeHasMore", activePage.hasNext());
        model.addAttribute("archivedHasMore", archivedPage.hasNext());
        model.addAttribute("q", query);   // ← для hidden input у "Завантажити ще"
        return "notes";
    }

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
        List<Note> familyNotes = noteService.getFamilySharedNotes(currentUser);

        model.addAttribute("family", family);
        model.addAttribute("familyNotes", familyNotes);
        model.addAttribute("currentUser", currentUser);
        return "family/notes";
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
}