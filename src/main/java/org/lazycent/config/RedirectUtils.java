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
 *   <li>Absolute URL (https://...) — приймаємо, ТІЛЬКИ якщо хост і порт
 *       збігаються з поточним запитом. Це потрібно, бо браузер завжди
 *       шле повний URL у Referer, а не просто path.</li>
 *   <li>Protocol-relative (//evil.com/path) — завжди відкидаємо.</li>
 *   <li>Інший хост — відкидаємо.</li>
 * </ul>
 *
 * <h3>Приклади (запит з localhost:8081)</h3>
 * <pre>
 *   /budget                                → redirect:/budget                    ✅
 *   http://localhost:8081/home             → redirect:/home                      ✅ (свій)
 *   http://localhost:8081/day/2026-10-03   → redirect:/day/2026-10-03            ✅ (свій)
 *   https://evil.com/phishing              → redirect:/                           ❌ (чужий)
 *   //evil.com/path                        → redirect:/                           ❌ (protocol-relative)
 *   javascript:alert(1)                    → redirect:/                           ❌ (не http)
 * </pre>
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
            if (uri.isAbsolute()) {
                if (!isSameOrigin(uri, request)) {
                    log.debug("Referer відкинуто (інший origin): {}", referer);
                    return "redirect:/";
                }
            }

            String path = uri.getPath();
            String query = uri.getQuery();

            // Додаткова страховка: path має бути внутрішнім
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                log.debug("Referer відкинуто (не внутрішній шлях): {}", referer);
                return "redirect:/";
            }

            String target = (query != null && !query.isEmpty())
                    ? path + "?" + query
                    : path;

            return "redirect:" + target;
        } catch (URISyntaxException e) {
            log.warn("Невалідний Referer: {}", referer);
            return "redirect:/";
        }
    }

    /**
     * Перевіряє, що URI вказує на той самий хост і порт, що й поточний запит.
     * Враховує, що порт може бути не вказаний (тоді — дефолтний для схеми).
     */
    private boolean isSameOrigin(URI uri, HttpServletRequest request) {
        String scheme = uri.getScheme();
        // Дозволяємо тільки http/https
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase(request.getServerName())) {
            return false;
        }

        int port = uri.getPort();
        int requestPort = request.getServerPort();
        if (port == -1) {
            // Дефолтний порт для схеми
            port = "https".equals(scheme) ? 443 : 80;
        }
        return port == requestPort;
    }
}