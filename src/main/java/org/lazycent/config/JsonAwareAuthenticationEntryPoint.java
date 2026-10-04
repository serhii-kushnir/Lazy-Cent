package org.lazycent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.lazycent.model.dto.ApiErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Розумний entry point:
 * <ul>
 *   <li>Якщо запит очікує JSON ({@code Accept: application/json} або {@code X-Requested-With: XMLHttpRequest}) —
 *       віддає 401 з JSON-тілом</li>
 *   <li>Інакше — стандартний редірект на /login</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JsonAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        if (expectsJson(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiErrorDto body = ApiErrorDto.of(
                    401, "Unauthorized",
                    "Потрібна автентифікація",
                    request.getRequestURI());

            objectMapper.writeValue(response.getWriter(), body);
        } else {
            redirectStrategy.sendRedirect(request, response, "/login");
        }
    }

    private boolean expectsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(xRequestedWith);
    }
}