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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        log.warn("Access denied: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                ex.getMessage() != null ? ex.getMessage() : "У вас немає доступу до цього ресурсу");
        return redirectToReferer(request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Сторінка не знайдена: {}", ex.getResourcePath());
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException ex, HttpServletRequest request,
                                          RedirectAttributes redirectAttributes) {
        log.warn("Business error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getUserMessage());
        return redirectToReferer(request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request,
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request,
                                               RedirectAttributes redirectAttributes) {
        log.warn("Data integrity error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.category.inUse"));
        return redirectToReferer(request);
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {
        log.error("Unexpected error: ", ex);
        redirectAttributes.addFlashAttribute("errorMessage", msg("error.technical"));
        return redirectToReferer(request);
    }

    private String msg(String key) {
        try {
            return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            return key;
        }
    }

    private String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty() && referer.startsWith("/")) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Дані були змінені в іншій сесії. Оновіть сторінку та спробуйте ще раз.");
        return redirectToReferer(request);
    }


}