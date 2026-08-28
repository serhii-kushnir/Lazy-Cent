// controller/NoteController.java
package org.lazycent.controller;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.Note;
import org.lazycent.model.User;
import org.lazycent.service.NoteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public String viewNotes(@AuthenticationPrincipal User currentUser, Model model) {
        model.addAttribute("notes", noteService.getActiveNotes(currentUser));
        model.addAttribute("archivedNotes", noteService.getArchivedNotes(currentUser));
        return "notes";
    }

    @PostMapping("/create")
    public String createNote(@RequestParam String title,
                             @RequestParam(required = false) String content,
                             @RequestParam(required = false) String color,
                             @RequestParam(required = false) String textColor,
                             @AuthenticationPrincipal User currentUser) {
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
                             @AuthenticationPrincipal User currentUser) {
        Note note = noteService.getNoteById(id, currentUser);
        note.setTitle(title);
        note.setContent(content != null ? content : "");
        if (color != null && !color.isEmpty()) {
            note.setColor(color);
        }
        if (textColor != null && !textColor.isEmpty()) {
            note.setTextColor(textColor);
        }
        noteService.updateNote(note);
        return "redirect:/notes";
    }

    @PostMapping("/delete/{id}")
    public String deleteNote(@PathVariable Long id,
                             @AuthenticationPrincipal User currentUser) {
        Note note = noteService.getNoteById(id, currentUser);
        noteService.deleteNote(id);
        return "redirect:/notes";
    }

    @PostMapping("/toggle-pin/{id}")
    public String togglePin(@PathVariable Long id,
                            @AuthenticationPrincipal User currentUser) {
        noteService.togglePinned(id);
        return "redirect:/notes";
    }

    @PostMapping("/toggle-archive/{id}")
    public String toggleArchive(@PathVariable Long id,
                                @AuthenticationPrincipal User currentUser) {
        noteService.toggleArchived(id);
        return "redirect:/notes";
    }
}