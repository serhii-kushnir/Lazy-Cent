package org.lazycent.repository;

import org.lazycent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByProviderId(String providerId);

    // ==================== ТОЧКОВІ UPDATE (не чіпають колекції) ====================

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.preferredCurrency = :currency WHERE u.id = :id")
    int updatePreferredCurrency(@Param("id") Long id, @Param("currency") String currency);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.preferredLanguage = :language WHERE u.id = :id")
    int updatePreferredLanguage(@Param("id") Long id, @Param("language") String language);
}