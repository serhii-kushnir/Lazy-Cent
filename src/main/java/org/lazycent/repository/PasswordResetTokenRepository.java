package org.lazycent.repository;

import org.lazycent.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUserId(Long userId);

    /**
     * Видаляє всі токени, у яких {@code expiryDate < threshold}.
     *
     * <p>Використовується {@code @Scheduled}-завданням для очищення
     * прострочених токенів. {@code @Modifying} — бо це bulk delete
     * (один SQL, не SELECT + N DELETE).
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryDate < :threshold")
    int deleteByExpiryDateBefore(@Param("threshold") LocalDateTime threshold);
}