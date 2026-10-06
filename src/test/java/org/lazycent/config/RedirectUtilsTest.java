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
        redirectUtils = new RedirectUtils();
    }

    /**
     * MockHttpServletRequest за замовчуванням має serverName = "localhost",
     * serverPort = 80, scheme = "http". Налаштовуємо під ці значення.
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
        // Браузер ЗАВЖДИ шле повний URL — це має працювати
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
            "https://evil.com/phishing",           // інший хост
            "http://evil.com",                     // інший хост
            "http://localhost:9999/home",          // свій хост, але чужий порт
            "//evil.com/path",                     // protocol-relative
            "javascript:alert(1)",                 // не http(s)
            "data:text/html,<script>alert(1)</script>",
            "ftp://localhost/file",                // не http(s)
            "\\\\evil.com\\share"                  // не URI
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
}