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

    private MockHttpServletRequest requestWithReferer(String referer) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/budget");
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

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/phishing",
            "http://evil.com",
            "//evil.com/path",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "ftp://evil.com",
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
}