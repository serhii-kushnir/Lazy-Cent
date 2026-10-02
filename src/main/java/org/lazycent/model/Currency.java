package org.lazycent.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Підтримувані валюти. Єдине джерело істини для:
 * <ul>
 *   <li>валідації при зміні валюти</li>
 *   <li>символу валюти (₴, $, €...)</li>
 *   <li>списку у формі (через {@link #codes()})</li>
 * </ul>
 */
@Getter
public enum Currency {

    UAH("₴", "Українська гривня"),
    USD("$", "US Dollar"),
    EUR("€", "Euro"),
    GBP("£", "British Pound"),
    PLN("zł", "Polish Złoty"),
    CZK("Kč", "Czech Koruna");

    private final String symbol;
    private final String displayName;

    Currency(String symbol, String displayName) {
        this.symbol = symbol;
        this.displayName = displayName;
    }

    /**
     * Парсинг коду валюти. Повертає {@link Optional#empty()} для невалідного коду.
     * Регістр не має значення: "uah", "UAH", "Uah" — все приймається.
     */
    public static Optional<Currency> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(c -> c.name().equals(normalized))
                .findFirst();
    }

    /**
     * @return код валюти за замовчуванням для нових користувачів.
     */
    public static Currency defaultCurrency() {
        return UAH;
    }

    /**
     * @return масив кодів для `<option>` у формі.
     */
    public static String[] codes() {
        return Arrays.stream(values()).map(Enum::name).toArray(String[]::new);
    }
}