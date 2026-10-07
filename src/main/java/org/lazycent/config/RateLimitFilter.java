package org.lazycent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простий in-memory rate limiter для POST /login та POST /register.
 * <p>
 * Алгоритм: sliding window — не більше N спроб за M секунд з однієї IP.
 * <p>
 * Рахуємо ТІЛЬКИ POST-запити, щоб редірект на GET /login?ratelimited=true
 * не потрапив у той самий фільтр і не створив цикл.
 *
 * <h3>Memory management</h3>
 * Ключі (IP) живуть у мапі доти, доки {@code @Scheduled} cleanup не прибере
 * ті, у яких всі спроби старші за вікно. Без cleanup мапа росла б вічно
 * при кожному новому IP — типовий memory leak.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;

    /** Hard cap на розмір мапи — захист від DoS через спам унікальними IP. */
    private static final int MAX_TRACKED_IPS = 10_000;

    /**
     * Довіряти заголовку X-Forwarded-For тільки якщо застосунок справді
     * стоїть за reverse proxy. За замовчуванням false (безпечно).
     *
     * <p>Property уніфіковано з {@link RedirectUtils} під спільним ім'ям
     * {@code app.trust-proxy}. Раніше було окреме
     * {@code app.rate-limit.trust-forwarded-for}.
     */
    @Value("${app.trust-proxy:false}")
    private boolean trustForwardedFor;

    private final Map<String, Deque<Long>> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolveServletPath(request);
        boolean isAuthPath = "/login".equals(path) || "/register".equals(path);
        boolean isPost = "POST".equalsIgnoreCase(request.getMethod());
        return !(isAuthPath && isPost);
    }

    /**
     * Визначає шлях запиту БЕЗ context-path.
     * <p>
     * У реальному Tomcat {@code getServletPath()} повертає коректне значення
     * (наприклад, {@code /login} навіть якщо застосунок під {@code /lazycent}).
     * Але {@code MockHttpServletRequest} у тестах має порожній {@code servletPath},
     * тому fallback: беремо {@code requestURI} і відрізаємо {@code contextPath},
     * якщо він є.
     */
    private String resolveServletPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            return servletPath;
        }

        String requestUri = request.getRequestURI();
        if (requestUri == null) {
            return "";
        }

        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = getClientIp(request);
        long now = System.currentTimeMillis();

        // Захист від DoS через спам унікальними IP: якщо мапа вже повна —
        // пропускаємо запит без трекінгу (fail-open), щоб не завалити сервер.
        if (attemptsByIp.size() >= MAX_TRACKED_IPS && !attemptsByIp.containsKey(ip)) {
            log.warn("Rate limit map досяг максимуму ({}), запит з {} не трекається",
                    MAX_TRACKED_IPS, ip);
            filterChain.doFilter(request, response);
            return;
        }

        Deque<Long> timestamps = attemptsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());

        boolean blocked;
        synchronized (timestamps) {
            // Прибираємо старі спроби (поза вікном)
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
            // FIX: коректний separator, якщо в URL уже є query string
            // (наприклад, /login?error → /login?error&ratelimited=true,
            // а не /login?error?ratelimited=true).
            String separator = request.getQueryString() == null ? "?" : "&";
            response.sendRedirect(request.getRequestURI() + separator + "ratelimited=true");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Періодичний cleanup мапи, щоб порожні Deque-и (і ключі) не накопичувались.
     * <p>
     * Запускається раз на 5 хвилин. Проходимо по всіх записах, видаляємо
     * протерміновані timestamps, а якщо Deque спорожнів — прибираємо і сам ключ.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        int removedKeys = 0;

        Iterator<Map.Entry<String, Deque<Long>>> it = attemptsByIp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> timestamps = entry.getValue();

            synchronized (timestamps) {
                while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > WINDOW_MS) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    it.remove();
                    removedKeys++;
                }
            }
        }

        if (removedKeys > 0) {
            log.debug("Rate limit cleanup: видалено {} записів, залишилось {}",
                    removedKeys, attemptsByIp.size());
        }
    }

    /**
     * Витягує IP клієнта з запиту.
     * <p>
     * За замовчуванням — {@code request.getRemoteAddr()} (безпечно без проксі).
     * Якщо застосунок за reverse proxy (Nginx/Caddy/Cloudflare) — увімкнути
     * {@code app.rate-limit.trust-forwarded-for=true} і переконатися, що проксі
     * перезаписує {@code X-Forwarded-For}, а не додає до клієнтського.
     */
    private String getClientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}