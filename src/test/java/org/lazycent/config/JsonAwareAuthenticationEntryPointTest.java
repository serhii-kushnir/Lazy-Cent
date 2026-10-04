package org.lazycent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAwareAuthenticationEntryPointTest {

    private JsonAwareAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        // Після зміни ApiErrorDto на String timestamp — JSR310-модуль не потрібен.
        // Підходить будь-який ObjectMapper, включно з `new ObjectMapper()`.
        entryPoint = new JsonAwareAuthenticationEntryPoint(new ObjectMapper());
    }

    @Test
    void commence_returns401Json_whenAcceptHeaderIsJson() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/categories/expense");
        req.addHeader("Accept", "application/json");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        entryPoint.commence(req, resp, new BadCredentialsException("nope"));

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).contains("application/json");
        assertThat(resp.getContentAsString()).contains("\"status\":401");
    }

    @Test
    void commence_returns401Json_whenXRequestedWithHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/categories/expense");
        req.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        entryPoint.commence(req, resp, new BadCredentialsException("nope"));

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentAsString()).contains("\"status\":401");
    }

    @Test
    void commence_redirectsToLogin_whenPlainBrowserRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/budget");
        req.addHeader("Accept", "text/html");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        entryPoint.commence(req, resp, new BadCredentialsException("nope"));

        assertThat(resp.getStatus()).isEqualTo(302);
        assertThat(resp.getRedirectedUrl()).isEqualTo("/login");
    }
}