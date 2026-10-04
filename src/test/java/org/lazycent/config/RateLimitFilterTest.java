package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
}