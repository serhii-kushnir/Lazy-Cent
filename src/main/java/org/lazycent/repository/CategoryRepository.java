package org.lazycent.repository;

import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // ---- Методи з фільтром по власнику ----

    List<Category> findByTypeAndOwner(String type, User owner);

    Optional<Category> findByNameAndOwner(String name, User owner);

    Optional<Category> findByNameIgnoreCaseAndOwner(String name, User owner);

    List<Category> findAllByOwner(User owner);

    long countByOwner(User owner);
}