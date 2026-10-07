package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectUtilsTest {

    private RedirectUtils redirectUtils;

    @BeforeEach
    void setUp() {
        // За замовчуванням — без довіри до X-Forwarded-* (безпечно)
        redirectUtils = new RedirectUtils(false);
    }

    /**
     * MockHttpServletRequest за замовчуванням має serverName = "localhost",
     * serverPort = 80, scheme = "http". Налаштовуємо під наші тести.
     */
    private MockHttpServletRequest requestWithReferer(String referer) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("localhost");
        req.setServerPort(8081);
        req.setScheme("http");
        if (referer != null) {
            req.addHeader("Referer", referer);
        }
        return req;
    }

    @Test
    void nullReferer_redirectsToRoot() {
        assertThat(redirectUtils.redirectToReferer(requestWithReferer(null)))
                .isEqualTo("redirect:/");
    }

    @Test
    void emptyReferer_redirectsToRoot() {
        assertThat(redirectUtils.redirectToReferer(requestWithReferer("")))
                .isEqualTo("redirect:/");
    }

    @Test
    void relativePath_isAllowed() {
        assertThat(redirectUtils.redirectToReferer(requestWithReferer("/budget")))
                .isEqualTo("redirect:/budget");
    }

    @Test
    void relativePathWithQuery_isAllowed() {
        assertThat(redirectUtils.redirectToReferer(requestWithReferer("/day/2026-10-03?tab=cards")))
                .isEqualTo("redirect:/day/2026-10-03?tab=cards");
    }

    @Test
    void fullUrl_sameOrigin_isReducedToPath() {
        assertThat(redirectUtils.redirectToReferer(
                requestWithReferer("http://localhost:8081/home")))
                .isEqualTo("redirect:/home");
    }

    @Test
    void fullUrl_sameOriginWithQuery_isReducedToPathWithQuery() {
        assertThat(redirectUtils.redirectToReferer(
                requestWithReferer("http://localhost:8081/budget?month=10&year=2026")))
                .isEqualTo("redirect:/budget?month=10&year=2026");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/phishing",
            "http://evil.com",
            "http://localhost:9999/home",          // свій хост, але чужий порт
            "//evil.com/path",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "ftp://localhost/file",
            "\\\\evil.com\\share"
    })
    void maliciousReferer_redirectsToRoot(String referer) {
        assertThat(redirectUtils.redirectToReferer(requestWithReferer(referer)))
                .isEqualTo("redirect:/");
    }

    @Test
    void malformedUri_redirectsToRoot() {
        assertThat(redirectUtils.redirectToReferer(requestWithReferer("http://[invalid")))
                .isEqualTo("redirect:/");
    }

    // ==================== P1: REVERSE PROXY ====================

    /**
     * За проксі (trustProxy=true) {@code request.getServerName()} = внутрішній,
     * а {@code X-Forwarded-Host} = зовнішній. Маємо порівнювати з forwarded.
     */
    @Test
    void forwardedHost_isUsed_whenTrustProxyTrue() {
        RedirectUtils proxyAware = new RedirectUtils(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("internal-container");   // внутрішнє ім'я
        req.setServerPort(8080);
        req.setScheme("http");
        req.addHeader("X-Forwarded-Host", "lazy-cent.fly.dev");
        req.addHeader("X-Forwarded-Port", "443");
        req.addHeader("X-Forwarded-Proto", "https");
        req.addHeader("Referer", "https://lazy-cent.fly.dev/budget");

        assertThat(proxyAware.redirectToReferer(req))
                .as("За проксі Referer свого ж хоста має бути прийнятий")
                .isEqualTo("redirect:/budget");
    }

    @Test
    void forwardedHost_withPort_parsedCorrectly() {
        RedirectUtils proxyAware = new RedirectUtils(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Host", "example.com:8443");
        req.addHeader("Referer", "https://example.com:8443/home");

        assertThat(proxyAware.redirectToReferer(req))
                .isEqualTo("redirect:/home");
    }

    @Test
    void forwardedHost_listOfHosts_firstIsUsed() {
        RedirectUtils proxyAware = new RedirectUtils(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Host", "public.example.com, internal.example.com");
        req.addHeader("X-Forwarded-Port", "443");
        req.addHeader("Referer", "https://public.example.com/home");

        assertThat(proxyAware.redirectToReferer(req))
                .isEqualTo("redirect:/home");
    }

    @Test
    void forwardedProtoHttps_usesDefaultPort443_whenNoExplicitPort() {
        RedirectUtils proxyAware = new RedirectUtils(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Host", "example.com");
        req.addHeader("X-Forwarded-Proto", "https");
        // Без X-Forwarded-Port → fallback на 443 за протоколом
        req.addHeader("Referer", "https://example.com/home");

        assertThat(proxyAware.redirectToReferer(req))
                .isEqualTo("redirect:/home");
    }

    @Test
    void forwardedHeaders_areIgnored_whenTrustProxyFalse() {
        // Класичний сценарій: застосунок НЕ за проксі, але атакуючий
        // підробив X-Forwarded-Host. Маємо проігнорувати.
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("localhost");
        req.setServerPort(8081);
        req.addHeader("X-Forwarded-Host", "evil.com");
        req.addHeader("Referer", "https://evil.com/phishing");

        assertThat(redirectUtils.redirectToReferer(req))
                .as("Без trustProxy forwarded headers ігноруються → evil.com відкидається")
                .isEqualTo("redirect:/");
    }

    @Test
    void forwardedHost_differentHost_stillRejected() {
        RedirectUtils proxyAware = new RedirectUtils(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
        req.setServerName("internal");
        req.setServerPort(8080);
        req.addHeader("X-Forwarded-Host", "our-domain.com");
        req.addHeader("Referer", "https://evil.com/phishing");

        assertThat(proxyAware.redirectToReferer(req))
                .isEqualTo("redirect:/");
    }
}