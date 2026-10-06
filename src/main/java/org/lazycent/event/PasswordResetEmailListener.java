package org.lazycent.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.service.MailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Надсилає лист для відновлення пароля ПІСЛЯ успішного commit'у транзакції.
 *
 * <p><b>Чому не прямий виклик з сервісу:</b> {@link MailService} асинхронний,
 * і якщо викликати його до commit'у, лист може піти з токеном, якого
 * в БД немає (при відкаті транзакції).
 *
 * <p><b>Чому AFTER_COMMIT:</b> гарантує, що лист піде тільки якщо токен
 * справді збережено. При rollback — не піде взагалі.
 *
 * <p><b>Чому fallbackExecution=false (default):</b> якщо раптом хтось
 * викличе публікацію події без активної транзакції — краще, щоб listener
 * мовчки не спрацював, ніж відправив лист у невідомому стані.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailListener {

    private final MailService mailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.debug("AFTER_COMMIT: надсилаю лист для відновлення пароля на {}", event.email());
        // sendPasswordResetEmail сам @Async("mailExecutor") — тут не дублюємо
        mailService.sendPasswordResetEmail(event.email(), event.token());
    }
}