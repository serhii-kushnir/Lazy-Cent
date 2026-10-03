package org.lazycent.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyTest {

    @Test
    void defaultCurrency_isUah() {
        assertThat(Currency.defaultCurrency()).isEqualTo(Currency.UAH);
    }

    @Test
    void fromCode_returnsCorrectCurrency_forKnownCode() {
        assertThat(Currency.fromCode("USD")).contains(Currency.USD);
        assertThat(Currency.fromCode("eur")).contains(Currency.EUR);
        assertThat(Currency.fromCode(" uah ")).contains(Currency.UAH);
        assertThat(Currency.fromCode("czk")).contains(Currency.CZK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"XYZ", "", "   ", "usd2", "US D"})
    void fromCode_returnsEmpty_forInvalidCode(String code) {
        assertThat(Currency.fromCode(code)).isEmpty();
    }

    @Test
    void fromCode_returnsEmpty_forNull() {
        assertThat(Currency.fromCode(null)).isEmpty();
    }

    @Test
    void symbols_areCorrect() {
        assertThat(Currency.UAH.getSymbol()).isEqualTo("₴");
        assertThat(Currency.USD.getSymbol()).isEqualTo("$");
        assertThat(Currency.EUR.getSymbol()).isEqualTo("€");
        assertThat(Currency.GBP.getSymbol()).isEqualTo("£");
        assertThat(Currency.PLN.getSymbol()).isEqualTo("zł");
        assertThat(Currency.CZK.getSymbol()).isEqualTo("Kč");
    }

    @Test
    void codes_containsAllCurrencies() {
        assertThat(Currency.codes())
                .containsExactlyInAnyOrder("UAH", "USD", "EUR", "GBP", "PLN", "CZK");
    }
}