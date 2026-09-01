package org.lazycent.repository;

import org.lazycent.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    
    Optional<PasswordResetToken> findByToken(String token);
    
    // !!! ВИПРАВЛЕНО: стандартне ім'я для Spring Data (без підкреслення) !!!
    void deleteByUserId(Long userId);
}