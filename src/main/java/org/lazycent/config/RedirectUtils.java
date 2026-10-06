package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Безпечний редірект на {@code Referer} з захистом від Open Redirect.
 *
 * <p><b>Політика:</b>
 * <ul>
 *   <li>Relative path без authority — приймаємо.</li>
 *   <li>Absolute URL (https://...) — приймаємо ТІЛЬКИ якщо хост і порт
 *       збігаються з поточним запитом.</li>
 *   <li>Protocol-relative (//evil.com/path) — завжди відкидаємо.</li>
 *   <li>Інший хост/порт — відкидаємо.</li>
 * </ul>
 */
@Component
@Slf4j
public class RedirectUtils {

    public String redirectToReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "redirect:/";
        }

        try {
            URI uri = new URI(referer);

            // Protocol-relative (//evil.com/path) — authority є, scheme немає
            if (uri.getScheme() == null && uri.getAuthority() != null) {
                log.debug("Referer відкинуто (protocol-relative): {}", referer);
                return "redirect:/";
            }

            // Absolute URL — перевіряємо, що це наш хост
            if (uri.isAbsolute() && !isSameOrigin(uri, request)) {
                log.debug("Referer відкинуто (інший origin): {}", referer);
                return "redirect:/";
            }

            String path = uri.getPath();
            String query = uri.getQuery();

            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                log.debug("Referer відкинуто (не внутрішній шлях): {}", referer);
                return "redirect:/";
            }

            return "redirect:" + (query != null && !query.isEmpty() ? path + "?" + query : path);
        } catch (URISyntaxException e) {
            log.warn("Невалідний Referer: {}", referer);
            return "redirect:/";
        }
    }

    private boolean isSameOrigin(URI uri, HttpServletRequest request) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase(request.getServerName())) {
            return false;
        }
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }
        return port == request.getServerPort();
    }
}