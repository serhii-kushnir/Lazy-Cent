package org.lazycent.config;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    // ==================== 403 ====================

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        log.warn("Access denied: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                ex.getMessage() != null ? ex.getMessage() : msg("error.access.denied"));
        return redirectToReferer(request);
    }

    // ==================== 404 ====================

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Сторінка не знайдена: {}", ex.getResourcePath());
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    // ==================== 400: business ====================

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException ex,
                                          HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        log.warn("Business error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getUserMessage());
        return redirectToReferer(request);
    }

    // ==================== 400: Bean Validation ====================

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

        return redirectToReferer(request);
    }

    // ==================== 409: integrity ====================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                               HttpServletRequest request,
                                               RedirectAttributes redirectAttributes) {
        log.warn("Data integrity error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.category.inUse"));
        return redirectToReferer(request);
    }

    // ==================== 409: optimistic lock ====================

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Дані були змінені в іншій сесії. Оновіть сторінку та спробуйте ще раз.");
        return redirectToReferer(request);
    }

    // ==================== 500 ====================

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex,
                                         HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {
        log.error("Unexpected error: ", ex);
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.technical"));
        return redirectToReferer(request);
    }

    // ==================== ДОПОМІЖНІ ====================

    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Редірект на Referer з підтримкою повних URL (http://host/path?query).
     * <p>
     * <b>FIX:</b> стара версія перевіряла {@code referer.startsWith("/")}, що майже
     * завжди {@code false} для справжнього заголовка {@code Referer} (він завжди
     * з хостом і схемою). Тепер парсимо URI і беремо тільки path + query.
     * <p>
     * Захист від Open Redirect: дозволяємо тільки відносні шляхи (починаються з одного `/`,
     * але не з `//`, які інтерпретуються як protocol-relative URL на чужий хост).
     */
    private String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isEmpty()) {
            return "redirect:/";
        }

        try {
            URI uri = new URI(referer);
            String path = uri.getPath();
            String query = uri.getQuery();

            // Дозволяємо тільки внутрішні шляхи
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return "redirect:/";
            }

            String target = (query != null && !query.isEmpty())
                    ? path + "?" + query
                    : path;

            return "redirect:" + target;
        } catch (URISyntaxException e) {
            log.warn("Невалідний Referer: {}", referer);
            return "redirect:/";
        }
    }
}