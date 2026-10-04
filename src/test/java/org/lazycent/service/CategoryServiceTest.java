package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.Category;
import org.lazycent.model.User;
import org.lazycent.repository.CategoryRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        category = new Category();
        category.setId(10L);
        category.setName("Їжа");
        category.setType("EXPENSE");
        category.setOwner(user);
    }

    // ==================== addCategory ====================

    @Test
    void addCategory_savesNewCategory_whenValid() {
        when(categoryRepository.findByNameIgnoreCaseAndOwner("Їжа", user))
                .thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.addCategory("Їжа", "expense", user);

        assertThat(result.getName()).isEqualTo("Їжа");
        assertThat(result.getType()).isEqualTo("EXPENSE");
        assertThat(result.getOwner()).isEqualTo(user);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void addCategory_throwsValidation_whenNameIsEmpty() {
        assertThatThrownBy(() -> categoryService.addCategory("   ", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("порожньою");
    }

    @Test
    void addCategory_throwsValidation_whenDuplicate() {
        when(categoryRepository.findByNameIgnoreCaseAndOwner("Їжа", user))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.addCategory("Їжа", "EXPENSE", user))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    // ==================== getCategoryById ====================

    @Test
    void getCategoryById_returnsCategory_whenOwnerMatches() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategoryById(10L, user);

        assertThat(result).isEqualTo(category);
    }

    @Test
    void getCategoryById_throwsEntityNotFound_whenNotExists() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(99L, user))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getCategoryById_throwsAccessDenied_whenDifferentOwner() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.getCategoryById(10L, otherUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("не належить");
    }

    // ==================== deleteCategory ====================

    @Test
    void deleteCategory_deletesWhenOwnerMatches() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(10L, user);

        verify(categoryRepository).deleteById(10L);
    }

    @Test
    void deleteCategory_throwsAccessDenied_forOtherOwner() {
        User otherUser = new User();
        otherUser.setId(2L);

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory(10L, otherUser))
                .isInstanceOf(AccessDeniedException.class);

        verify(categoryRepository, never()).deleteById(anyLong());
    }
}