package org.lazycent.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

/**
 * Кеш категорій з TTL 5 хвилин і обмеженням у 500 записів.
 * Ключ = user.id, тому 500 — це ~500 активних користувачів.
 *
 * <h3>Чому {@code @Profile("!test")}</h3>
 * У профілі {@code test} кеш ВИМКНЕНИЙ з двох причин:
 * <ol>
 *   <li>{@code @EnableCaching} створює CGLIB-проксі навколо {@code CategoryService}.
 *       Це ламає {@code @MockBean CategoryService} — проксі продовжує делегувати
 *       виклик у реальний бін, а не в мок. Результат: тести на помилки отримують
 *       {@code 200 []} замість очікуваних 4xx/5xx.</li>
 *   <li>Кеш між тестами треба чистити, а це додатковий boilerplate. Простіше
 *       вимкнути.</li>
 * </ol>
 */
@Configuration
@EnableCaching
@Profile("!test")
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "expenseCategories",
                "incomeCategories"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(500));
        return manager;
    }
}