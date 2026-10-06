package org.lazycent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Прокладає {@code traceId} через усі логи одного HTTP-запиту.
 *
 * <p>Пріоритет: беремо {@code X-Request-Id} із заголовка (якщо клієнт
 * прислав) — це дозволяє корелювати логи фронта й бекенда. Якщо немає —
 * генеруємо UUID.
 *
 * <p>{@code traceId} кладеться в MDC під ключем {@code traceId}, звідки
 * його бере logback ({@code %X{traceId}}) і {@link org.lazycent.model.dto.ApiErrorDto}.
 *
 * <p>Також повертаємо {@code X-Request-Id} у відповіді, щоб клієнт міг
 * показати користувачу в разі помилки (для support-запитів).
 *
 * <p>Порядок: {@code HIGHEST_PRECEDENCE} — фільтр має бути ПЕРШИМ, щоб
 * навіть security-фільтри логувались із traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}