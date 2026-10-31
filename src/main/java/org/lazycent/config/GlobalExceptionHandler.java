package org.lazycent.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.lazycent.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;
    private final RedirectUtils redirectUtils;   // ← інжект через конструктор

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        log.warn("Access denied: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                ex.getMessage() != null ? ex.getMessage() : msg("error.access.denied"));
        return redirectUtils.redirectToReferer(request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(NoResourceFoundException ex,
                                        HttpServletResponse response) {
        String path = ex.getResourcePath();

        // Тихо ігноруємо .map файли — браузер запитує їх лише при відкритих DevTools,
        // і вони не потрібні для роботи застосунку.
        if (path != null && path.endsWith(".map")) {
            return ResponseEntity.notFound().build();   // 404 без рендерингу шаблону
        }

        log.debug("Сторінка не знайдена: {}", path);
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        log.warn("Business error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getUserMessage());
        return redirectUtils.redirectToReferer(request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationExceptions(MethodArgumentNotValidException ex,
                                             HttpServletRequest request,
                                             RedirectAttributes redirectAttributes) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        redirectAttributes.addFlashAttribute("fieldErrors", fieldErrors);
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.form.invalid"));

        return redirectUtils.redirectToReferer(request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                               HttpServletRequest request,
                                               RedirectAttributes redirectAttributes) {
        log.warn("Data integrity error: {}", ex.getMessage());
        // Fallback: pre-check у CategoryService.deleteCategory покриває
        // типовий кейс. Сюди потрапляємо при race condition або інших FK.
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.data.integrity"));
        return redirectUtils.redirectToReferer(request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.optimistic.lock"));
        return redirectUtils.redirectToReferer(request);
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         RedirectAttributes redirectAttributes) {
        log.error("Unexpected error: ", ex);

        // Якщо відповідь уже відправлена — редірект неможливий,
        // спроба addFlashAttribute кине "Cannot create a session after response committed"
        if (response.isCommitted()) {
            log.error("Response already committed, cannot redirect from error handler");
            return null;   // DispatcherServlet завершить обробку без exceptions
        }

        redirectAttributes.addFlashAttribute("errorMessage", msg("error.technical"));
        return redirectUtils.redirectToReferer(request);
    }

    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }
}