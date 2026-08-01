package org.lazycent.repository;

import org.lazycent.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByType(String type);
    Optional<Category> findByName(String name);

    // Новий метод для регістронезалежного пошуку
    Optional<Category> findByNameIgnoreCase(String name);
}