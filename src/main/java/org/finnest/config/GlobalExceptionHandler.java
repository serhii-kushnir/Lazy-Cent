package org.finnest.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RuntimeException.class)
    public String handleRuntimeException(RuntimeException ex, RedirectAttributes redirectAttributes) {
        log.error("RuntimeException caught: ", ex);
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/";
    }

    // Можна додати обробку інших винятків, наприклад:
    // @ExceptionHandler(DataIntegrityViolationException.class)
    // public String handleDataIntegrityViolation(DataIntegrityViolationException ex, RedirectAttributes redirectAttributes) {
    //     redirectAttributes.addFlashAttribute("errorMessage", "Помилка цілісності даних: " + ex.getMessage());
    //     return "redirect:/";
    // }
}