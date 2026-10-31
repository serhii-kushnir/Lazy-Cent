package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
@Slf4j
public class RedirectUtils {

    private final boolean trustProxy;

    public RedirectUtils(@Value("${app.trust-proxy:false}") boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

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
        if (host == null) {
            return false;
        }

        // FIX (P1): за reverse proxy порівнюємо з X-Forwarded-Host, а не з
        // внутрішнім хостом контейнера.
        String requestHost = resolveHost(request);
        if (!host.equalsIgnoreCase(requestHost)) {
            return false;
        }

        // Порт: якщо в Referer не вказано — використовуємо дефолт для схеми.
        // Це дозволяє працювати навіть без X-Forwarded-Port, коли клієнт
        // шле referer без порту (типово для https://host/path).
        int refererPort = uri.getPort();
        if (refererPort == -1) {
            refererPort = "https".equals(scheme) ? 443 : 80;
        }

        int requestPort = resolvePort(request, scheme);
        return refererPort == requestPort;
    }

    /**
     * Хост поточного запиту з урахуванням проксі.
     *
     * <p>{@code X-Forwarded-Host} може бути:
     * <ul>
     *   <li>{@code example.com}</li>
     *   <li>{@code example.com:8443}</li>
     *   <li>{@code a.com, b.com} (список проксі) — беремо перше</li>
     * </ul>
     */
    private String resolveHost(HttpServletRequest request) {
        if (!trustProxy) {
            return request.getServerName();
        }
        String forwarded = request.getHeader("X-Forwarded-Host");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            int colon = first.indexOf(':');
            return colon > 0 ? first.substring(0, colon) : first;
        }
        return request.getServerName();
    }

    /**
     * Порт поточного запиту з урахуванням проксі.
     *
     * <p>Пріоритет джерел:
     * <ol>
     *   <li>{@code X-Forwarded-Port} — прямий</li>
     *   <li>Порт у {@code X-Forwarded-Host} ({@code host:port})</li>
     *   <li>{@code X-Forwarded-Proto} → дефолтний порт для нього (443/80)</li>
     *   <li>{@code request.getServerPort()} — fallback</li>
     * </ol>
     */
    private int resolvePort(HttpServletRequest request, String scheme) {
        if (!trustProxy) {
            return request.getServerPort();
        }

        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isBlank()) {
            try {
                return Integer.parseInt(forwardedPort.split(",")[0].trim());
            } catch (NumberFormatException e) {
                log.debug("Невалідний X-Forwarded-Port: {}", forwardedPort);
            }
        }

        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            String first = forwardedHost.split(",")[0].trim();
            int colon = first.indexOf(':');
            if (colon > 0) {
                try {
                    return Integer.parseInt(first.substring(colon + 1));
                } catch (NumberFormatException e) {
                    log.debug("Невалідний порт у X-Forwarded-Host: {}", forwardedHost);
                }
            }
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.trim()) ? 443 : 80;
        }

        return request.getServerPort();
    }
}