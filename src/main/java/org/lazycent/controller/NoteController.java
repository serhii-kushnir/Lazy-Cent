package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.service.NoteService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Контролер нотаток.
 * <p>
 * Наслідує {@link BaseController} — отримує {@code getCurrentUser()},
 * {@code redirectToReferer()} тощо.
 */
@Controller
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController extends BaseController {

    private final NoteService noteService;

    @GetMapping
    public String viewNotes(Authentication authentication, Model model) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("notes", noteService.getActiveNotes(currentUser));
        model.addAttribute("archivedNotes", noteService.getArchivedNotes(currentUser));
        return "notes";
    }

    @PostMapping("/create")
    public String createNote(@RequestParam String title,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String color,
                             @RequestParam(required = false) String textColor,
                             Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content != null ? content : "");
        note.setColor(color != null ? color : "#ffffff");
        note.setTextColor(textColor != null ? textColor : "#212529");
        note.setOwner(currentUser);
        noteService.createNote(note);
        return "redirect:/notes";
    }

    @PostMapping("/update/{id}")
    public String updateNote(@PathVariable Long id,
                             @RequestParam String title,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String color,
                             @RequestParam(required = false) String textColor,
                             Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        noteService.updateNote(id, title, content, color, textColor, currentUser);
        return "redirect:/notes";
    }

    @PostMapping("/delete/{id}")
    public String deleteNote(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        noteService.deleteNote(id, currentUser);
        return "redirect:/notes";
    }

    @PostMapping("/toggle-pin/{id}")
    public String togglePin(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        noteService.togglePinned(id, currentUser);
        return "redirect:/notes";
    }

    @PostMapping("/toggle-archive/{id}")
    public String toggleArchive(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return "redirect:/login";
        }
        noteService.toggleArchived(id, currentUser);
        return "redirect:/notes";
    }
}