package org.lazycent.model.dto;

/**
 * Один рядок у блоці «Топ категорій витрат» на /home.
 * percent — частка від суми всіх витрат за місяць (0..100).
 */
public record CategoryExpenseItem(
        String name,
        Double amount,
        double percent
) {}