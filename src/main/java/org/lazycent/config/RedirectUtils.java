package org.lazycent.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Безпечний редірект на {@code Referer} з захистом від Open Redirect.
 *
 * <p><b>Політика:</b> приймаємо ТІЛЬКИ relative references без схеми й без
 * authority (хоста). Усе інше — редірект на {@code /}.
 *
 * <p>Чому не "витягуємо тільки path" з абсолютного URL: це безпечно з точки
 * зору Open Redirect (Spring резолвить {@code redirect:/path} відносно нашого
 * хоста), але створює path-confusion — атакувальник може підсунути referer
 * зі шляхом на чутливий ендпоінт ({@code https://evil.com/logout}).
 * Строгіша політика — передбачуваніша.
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

            // 1. Абсолютний URL (має scheme) — https://evil.com, javascript:..., data:...
            // 2. Є authority/host — //evil.com/path (protocol-relative URL)
            if (uri.isAbsolute() || uri.getAuthority() != null) {
                log.debug("Referer відкинуто (абсолютний URL або з host): {}", referer);
                return "redirect:/";
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
}