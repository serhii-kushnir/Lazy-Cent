package org.lazycent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простий in-memory rate limiter для POST /login та POST /register.
 * Алгоритм: sliding window — не більше N спроб за M секунд з однієї IP.
 *
 * Рахуємо ТІЛЬКИ POST-запити, щоб редірект на GET /login?ratelimited=true
 * не потрапив у той самий фільтр і не створив цикл.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean isAuthPath = "/login".equals(path) || "/register".equals(path);
        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());
        return !(isAuthPath && isPost);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = getClientIp(request);
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = attemptsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());

        boolean blocked;
        synchronized (timestamps) {
            // Прибираємо старі спроби
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > WINDOW_MS) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= MAX_ATTEMPTS_PER_WINDOW) {
                blocked = true;
            } else {
                timestamps.addLast(now);
                blocked = false;
            }
        }

        if (blocked) {
            log.warn("Rate limit exceeded for IP: {} on {}", ip, request.getRequestURI());
            // Редірект на GET /login?ratelimited=true (GET пропустимо через shouldNotFilter)
            response.sendRedirect(request.getRequestURI() + "?ratelimited=true");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}