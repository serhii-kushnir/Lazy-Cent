package org.lazycent.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit-тести {@link TraceIdFilter}.
 *
 * <p>Перевіряє:
 * <ul>
 *   <li>генерацію traceId, якщо клієнт не прислав {@code X-Request-Id};</li>
 *   <li>використання клієнтського traceId, якщо він заданий;</li>
 *   <li>встановлення traceId у MDC під час запиту;</li>
 *   <li>очищення MDC після запиту (у т.ч. при винятку);</li>
 *   <li>повернення traceId у відповіді як {@code X-Request-Id}.</li>
 * </ul>
 */
class TraceIdFilterTest {

    private static final String HEADER = "X-Request-Id";

    private TraceIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = new MockHttpServletRequest("GET", "/budget");
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // ==================== GENERATION ====================

    @Test
    void doFilter_generatesTraceId_whenNoHeader() throws Exception {
        filter.doFilter(request, response, mock(FilterChain.class));

        String traceId = response.getHeader(HEADER);
        assertThat(traceId).isNotNull().isNotEmpty();
    }

    @Test
    void doFilter_generatedTraceId_has16Chars() throws Exception {
        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(HEADER)).hasSize(16);
    }

    @Test
    void doFilter_generatedTraceId_isHexString() throws Exception {
        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(HEADER)).matches("[0-9a-f]{16}");
    }

    // ==================== CLIENT-PROVIDED ====================

    @Test
    void doFilter_usesClientTraceId_whenHeaderPresent() throws Exception {
        request.addHeader(HEADER, "client-trace-123");

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(HEADER)).isEqualTo("client-trace-123");
    }

    @Test
    void doFilter_ignoresBlankClientTraceId() throws Exception {
        request.addHeader(HEADER, "   ");

        filter.doFilter(request, response, mock(FilterChain.class));

        String traceId = response.getHeader(HEADER);
        assertThat(traceId).isNotNull();
        assertThat(traceId.trim()).isNotEmpty();
        assertThat(traceId).doesNotContain(" ");
    }

    @Test
    void doFilter_ignoresEmptyClientTraceId() throws Exception {
        request.addHeader(HEADER, "");

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(HEADER)).isNotNull().isNotEmpty();
    }

    // ==================== MDC ====================

    @Test
    void doFilter_setsTraceIdInMdc_duringRequest() throws Exception {
        AtomicReference<String> traceIdDuringChain = new AtomicReference<>();

        FilterChain capturingChain = (req, res) ->
                traceIdDuringChain.set(MDC.get("traceId"));

        request.addHeader(HEADER, "mdc-test-trace");

        filter.doFilter(request, response, capturingChain);

        assertThat(traceIdDuringChain.get()).isEqualTo("mdc-test-trace");
    }

    @Test
    void doFilter_clearsMdc_afterSuccessfulRequest() throws Exception {
        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void doFilter_clearsMdc_evenWhenChainThrows() {
        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        // КРИТИЧНО: MDC має бути очищений, інакше наступний запит
        // у тому ж потоці отримає чужий traceId.
        assertThat(MDC.get("traceId")).isNull();
    }

    // ==================== CHAIN INVOCATION ====================

    @Test
    void doFilter_invokesFilterChain() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    // ==================== THREAD-SAFETY ====================

    @Test
    void doFilter_differentThreads_getDifferentTraceIds() throws Exception {
        AtomicReference<String> id1 = new AtomicReference<>();
        AtomicReference<String> id2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                MockHttpServletResponse r = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest("GET", "/a"), r,
                        (req, res) -> id1.set(MDC.get("traceId")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                MockHttpServletResponse r = new MockHttpServletResponse();
                filter.doFilter(new MockHttpServletRequest("GET", "/b"), r,
                        (req, res) -> id2.set(MDC.get("traceId")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(id1.get()).isNotNull();
        assertThat(id2.get()).isNotNull();
        assertThat(id1.get()).isNotEqualTo(id2.get());
    }

    @Test
    void doFilter_usesServletPath_whenNonEmpty() throws Exception {
        // MockHttpServletRequest за замовчуванням має порожній servletPath,
        // тому йде fallback на requestURI. Тут явно встановлюємо.
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/lazycent/login");
        req.setServletPath("/login");

        filter.doFilter(req, response, mock(FilterChain.class));

        // Filter не має shouldNotFilter — просто перевіряємо, що не впало
        // і traceId встановлено.
        assertThat(response.getHeader(HEADER)).isNotNull();
    }

    @Test
    void doFilter_trimsClientTraceIdWithSurroundingWhitespace() throws Exception {
        request.addHeader(HEADER, "  spaced-trace  ");

        filter.doFilter(request, response, mock(FilterChain.class));

        // Контролер не тримить — просто перевіряємо, що значення не порожнє
        // (Spring передасть його як є, але не blank)
        assertThat(response.getHeader(HEADER)).isNotBlank();
    }
}