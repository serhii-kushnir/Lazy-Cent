package org.lazycent.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

/**
 * Unit-тести {@link CacheConfig}.
 *
 * <p>Клас позначений {@code @Profile("!test")}, тож у Spring-контексті
 * з профілем "test" бін {@code cacheManager} не створюється. Тому
 * тестуємо через прямий виклик методу.
 */
class CacheConfigTest {

    private final CacheConfig config = new CacheConfig();

    @Test
    void cacheManager_isCaffeineCacheManager() {
        CacheManager manager = config.cacheManager();
        assertThat(manager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    void cacheManager_hasExpenseAndIncomeCaches() {
        CacheManager manager = config.cacheManager();

        Cache expenseCache = manager.getCache("expenseCategories");
        Cache incomeCache = manager.getCache("incomeCategories");

        assertThat(expenseCache).isNotNull();
        assertThat(incomeCache).isNotNull();
    }

    @Test
    void cacheManager_canStoreAndRetrieveValues() {
        CacheManager manager = config.cacheManager();
        Cache cache = manager.getCache("expenseCategories");

        cache.put(1L, "test-value");
        assertThat(cache.get(1L, String.class)).isEqualTo("test-value");
    }

    @Test
    void cacheManager_returnsNullForUnknownCache() {
        CacheManager manager = config.cacheManager();

        // CaffeineCacheManager з явним списком імен (через конструктор)
        // повертає null для невідомих кешів, а не створює їх на вимогу.
        Cache unknown = manager.getCache("nonexistent");
        assertThat(unknown).isNull();
    }

    @Test
    void cacheManager_doesNotThrowForUnknownCacheLookup() {
        CacheManager manager = config.cacheManager();

        assertThatCode(() -> manager.getCache("random-name"))
                .doesNotThrowAnyException();
    }
}