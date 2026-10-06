package org.lazycent.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTypeTest {

    @Test
    void values_containsExpenseAndIncome() {
        assertThat(TransactionType.values())
                .containsExactly(TransactionType.EXPENSE, TransactionType.INCOME);
    }

    @Test
    void valueOf_returnsCorrectConstant() {
        assertThat(TransactionType.valueOf("EXPENSE")).isEqualTo(TransactionType.EXPENSE);
        assertThat(TransactionType.valueOf("INCOME")).isEqualTo(TransactionType.INCOME);
    }

    @Test
    void name_returnsUpperCaseValue() {
        assertThat(TransactionType.EXPENSE.name()).isEqualTo("EXPENSE");
        assertThat(TransactionType.INCOME.name()).isEqualTo("INCOME");
    }

    @Test
    void valueOf_throwsForUnknownValue() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> TransactionType.valueOf("UNKNOWN"));
    }
}