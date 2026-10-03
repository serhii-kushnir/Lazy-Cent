package org.lazycent.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Кеш для категорій користувача.
 *
 * <p>Категорії рідко змінюються, але читаються на кожному рендері сторінки
 * (через GlobalControllerAdvice.populateExpenseCategories / populateIncomeCategories).
 * Кеш зменшує кількість SQL-запитів на 2 на кожен запит.
 *
 * <p>Cache key = user.id (щоб користувач A не бачив категорії користувача B).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(
                "expenseCategories",
                "incomeCategories"
        );
    }
}