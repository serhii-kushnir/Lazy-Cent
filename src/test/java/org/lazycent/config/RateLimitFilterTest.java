package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import static org.mockito.Mockito.*;


import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тест для {@link RateLimitFilter}.
 * <p>
 * Не піднімає Spring-контекст — просто перевіряє внутрішню логіку фільтра.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void shouldNotFilter_skipsNonAuthPaths() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_skipsGetOnLogin() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login");
        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void shouldNotFilter_processesPostOnLogin() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void shouldNotFilter_processesPostOnRegister() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/register");
        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void cleanupExpiredEntries_removesEmptyDeques() throws Exception {
        // Отримуємо доступ до приватної мапи через reflection
        // ВАЖЛИВО: приводимо до Map<String, Deque<Long>>, не Map<String, ?>
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> attemptsByIp = (Map<String, Deque<Long>>)
                getField(filter, "attemptsByIp").get(filter);

        // Симулюємо старі записи (з timestamps 2 хв тому)
        long oldTimestamp = System.currentTimeMillis() - 120_000;
        attemptsByIp.put("192.168.1.1", new ArrayDeque<>(List.of(oldTimestamp)));
        attemptsByIp.put("192.168.1.2", new ArrayDeque<>(List.of(oldTimestamp)));

        assertThat(attemptsByIp).hasSize(2);

        // Викликаємо cleanup вручну
        filter.cleanupExpiredEntries();

        // Всі записи мають бути видалені (бо timestamps старші за вікно)
        assertThat(attemptsByIp).isEmpty();
    }

    @Test
    void cleanupExpiredEntries_keepsRecentEntries() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> attemptsByIp = (Map<String, Deque<Long>>)
                getField(filter, "attemptsByIp").get(filter);

        long recentTimestamp = System.currentTimeMillis() - 1_000;   // 1 сек тому
        attemptsByIp.put("192.168.1.1", new ArrayDeque<>(List.of(recentTimestamp)));

        filter.cleanupExpiredEntries();

        assertThat(attemptsByIp).hasSize(1);
        assertThat(attemptsByIp).containsKey("192.168.1.1");
    }

    private static Field getField(Object target, String fieldName) throws NoSuchFieldException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    @Test
    void doFilter_passesThrough_whenRequestIsGet() throws Exception {
        // GET /login — shouldNotFilter=true → ланцюг одразу
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void doFilter_allowsUpToMaxAttempts() throws Exception {
        // MAX_ATTEMPTS_PER_WINDOW = 5 → перші 5 POST /login проходять
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
            req.setRemoteAddr("10.0.0." + i);   // різні IP → різні кошики
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
        }

        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void doFilter_blocksAfterMaxAttempts() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String sameIp = "192.168.99.99";

        // 5 дозволених спроб
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
            req.setRemoteAddr(sameIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
        }

        // 6-та — блокується (redirect на ?ratelimited=true)
        MockHttpServletRequest blocked = new MockHttpServletRequest("POST", "/login");
        blocked.setRemoteAddr(sameIp);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blocked, blockedRes, chain);

        assertThat(blockedRes.getRedirectedUrl()).contains("ratelimited=true");
    }

    @Test
    void getClientIp_usesXForwardedFor_whenTrustForwardedForTrue() throws Exception {
        // Встановлюємо приватне поле trustForwardedFor=true через reflection
        java.lang.reflect.Field field = RateLimitFilter.class.getDeclaredField("trustForwardedFor");
        field.setAccessible(true);
        field.setBoolean(filter, true);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        req.setRemoteAddr("10.0.0.1");

        // Викликаємо через reflection приватний метод
        java.lang.reflect.Method m = RateLimitFilter.class.getDeclaredMethod(
                "getClientIp", jakarta.servlet.http.HttpServletRequest.class);
        m.setAccessible(true);

        String ip = (String) m.invoke(filter, req);

        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    void getClientIp_usesXRealIp_whenXForwardedForMissing() throws Exception {
        java.lang.reflect.Field field = RateLimitFilter.class.getDeclaredField("trustForwardedFor");
        field.setAccessible(true);
        field.setBoolean(filter, true);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login");
        req.addHeader("X-Real-IP", "198.51.100.7");
        req.setRemoteAddr("10.0.0.1");

        java.lang.reflect.Method m = RateLimitFilter.class.getDeclaredMethod(
                "getClientIp", jakarta.servlet.http.HttpServletRequest.class);
        m.setAccessible(true);

        String ip = (String) m.invoke(filter, req);

        assertThat(ip).isEqualTo("198.51.100.7");
    }
}