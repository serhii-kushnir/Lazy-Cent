package org.lazycent.config;

import org.lazycent.exception.BusinessException;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 0. Обробка запитів на неіснуючі статичні ресурси (наприклад, .map файли,
    //    які браузер підвантажує сам для дебагу minified JS) — тихий 404,
    //    без ERROR-логу і без редіректів/flash-повідомлень.
    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Сторінка не знайдена: {}", ex.getResourcePath());
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    // 1. Обробка бізнес-винятків (повертаємо на попередню сторінку)
    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("Business error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getUserMessage());
        return redirectToReferer(request);
    }

    // 2. Обробка помилок валідації DTO (показуємо помилки біля полів)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("Validation error: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        redirectAttributes.addFlashAttribute("fieldErrors", fieldErrors);
        redirectAttributes.addFlashAttribute("errorMessage", "Будь ласка, виправте помилки у формі");

        return redirectToReferer(request);
    }

    // 3. Обробка помилок цілісності даних (наприклад, видалення категорії, що використовується)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("Data integrity error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "Неможливо виконати операцію. Можливо, ви намагаєтесь видалити категорію, яка вже використовується у транзакціях.");
        return redirectToReferer(request);
    }

    // 4. Загальний обробник для будь-яких непередбачуваних помилок
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.error("Unexpected error: ", ex); // повний стек-трейс у лог
        redirectAttributes.addFlashAttribute("errorMessage", "Сталася технічна помилка. Спробуйте пізніше.");
        return redirectToReferer(request);
    }

    // Допоміжний метод для безпечного редиректу на попередню сторінку
    private String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty() && referer.startsWith("/")) {
            return "redirect:" + referer;
        }
        // Якщо реферер відсутній або зовнішній – перенаправляємо на головну
        return "redirect:/";
    }
}