package org.lazycent.event;

/**
 * Подія: користувач запросив відновлення пароля.
 *
 * <p>Публікується {@code PasswordResetService} у межах транзакції.
 * Слухається {@code PasswordResetEmailListener} після успішного commit'у.
 *
 * <p><b>Чому record:</b> імʼютабельний, автоматичні {@code equals/hashCode/toString}.
 * Містить мінімум даних — тільки те, що треба для надсилання листа.
 */
public record PasswordResetRequestedEvent(String email, String token) {
}