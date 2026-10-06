package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lazycent.exception.EntityNotFoundException;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private DemoDataService demoDataService;
    @Mock private BudgetService budgetService;
    @Mock private NoteService noteService;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    private User managedUser;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        passwordEncoder = new BCryptPasswordEncoder();

        userService = new UserService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                demoDataService,
                budgetService,
                noteService,
                messageSource
        );

        managedUser = new User();
        managedUser.setId(1L);
        managedUser.setUsername("testuser");
        managedUser.setEmail("test@example.com");
        managedUser.setPassword(passwordEncoder.encode("OldPassword1!"));
        managedUser.setRole("USER");
        managedUser.setPreferredCurrency("UAH");
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // ==================== FINDERS ====================

    @Test
    void findByUsername_returnsUser_whenExists() {
        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(managedUser));

        assertThat(userService.findByUsername("testuser")).isSameAs(managedUser);
    }

    @Test
    void findByUsername_returnsNull_whenNotExists() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(userService.findByUsername("ghost")).isNull();
    }

    @Test
    void findByEmail_returnsUser_whenExists() {
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(managedUser));

        assertThat(userService.findByEmail("test@example.com")).isSameAs(managedUser);
    }

    @Test
    void findByEmail_returnsNull_whenNotExists() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThat(userService.findByEmail("ghost@example.com")).isNull();
    }

    // ==================== DEPRECATED ====================

    @Test
    @SuppressWarnings("deprecation")
    void registerUser_withoutEmail_delegatesToFullVersion() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User result = userService.registerUser("newuser", "Password1!");

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getEmail()).isNull();
        verify(demoDataService).createDefaultCategories(any(User.class));
    }

    @Test
    @SuppressWarnings("deprecation")
    void saveOAuth2User_delegatesToSaveOrUpdate() {
        when(userRepository.findByProviderAndProviderId("google", "g-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("g@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User result = userService.saveOAuth2User(
                "google", "g-123", "g@example.com", "Google User");

        assertThat(result.getProvider()).isEqualTo("google");
        assertThat(result.getProviderId()).isEqualTo("g-123");
    }

    // ==================== loadManagedUser ====================

    @Test
    void updateCurrency_throwsValidation_whenUserIsNull() {
        assertThatThrownBy(() -> userService.updateCurrency(null, "USD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ідентифікований");
    }

    @Test
    void updateCurrency_throwsValidation_whenUserIdIsNull() {
        User transientUser = new User();
        transientUser.setUsername("transient");

        assertThatThrownBy(() -> userService.updateCurrency(transientUser, "USD"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("збережений");
    }

    @Test
    void updateCurrency_throwsEntityNotFound_whenUserDeletedFromDb() {
        User stubUser = new User();
        stubUser.setId(99L);
        stubUser.setUsername("ghost");

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateCurrency(stubUser, "USD"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateCurrency_savesNewCurrency_whenValid() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateCurrency(managedUser, "USD");

        assertThat(managedUser.getPreferredCurrency()).isEqualTo("USD");
        verify(userRepository).save(managedUser);
    }

    @Test
    void updateCurrency_throwsValidation_whenCurrencyInvalid() {
        assertThatThrownBy(() -> userService.updateCurrency(managedUser, "XYZ"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("XYZ");

        verify(userRepository, never()).save(any());
    }

    // ==================== updateProfile ====================

    @Test
    void updateProfile_throwsValidation_whenUsernameEmpty() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        assertThatThrownBy(() -> userService.updateProfile(
                managedUser, "   ", "test@example.com", null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("порожнім");
    }

    @Test
    void updateProfile_throwsValidation_whenUsernameTaken() {
        User other = new User();
        other.setId(2L);
        other.setUsername("taken");

        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> userService.updateProfile(
                managedUser, "taken", null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");
    }

    @Test
    void updateProfile_throwsValidation_whenOnlyNewPasswordProvided() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        assertThatThrownBy(() -> userService.updateProfile(
                managedUser, "testuser", "test@example.com", null, "NewPassword1!"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("поточний");
    }

    @Test
    void updateProfile_throwsValidation_whenOnlyCurrentPasswordProvided() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        assertThatThrownBy(() -> userService.updateProfile(
                managedUser, "testuser", "test@example.com", "OldPassword1!", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("поточний");
    }

    @Test
    void updateProfile_throwsValidation_whenCurrentPasswordWrong() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        assertThatThrownBy(() -> userService.updateProfile(
                managedUser, "testuser", "test@example.com", "WrongPassword", "NewPassword1!"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Невірний");
    }

    @Test
    void updateProfile_changesUsernameOnly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));
        when(userRepository.findByUsername("newname")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(managedUser, "newname", "test@example.com", null, null);

        assertThat(managedUser.getUsername()).isEqualTo("newname");
        assertThat(passwordEncoder.matches("OldPassword1!", managedUser.getPassword())).isTrue();
    }

    @Test
    void updateProfile_changesPassword_whenBothProvided() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateProfile(managedUser, "testuser", "test@example.com",
                "OldPassword1!", "NewPassword1!");

        assertThat(passwordEncoder.matches("NewPassword1!", managedUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OldPassword1!", managedUser.getPassword())).isFalse();
    }

    @Test
    void updateProfile_throwsValidation_whenOAuth2User() {
        managedUser.setProvider("google");
        managedUser.setProviderId("123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        assertThatThrownBy(() -> userService.updateProfile(
                managedUser, "newname", null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OAuth2");
    }

    // ==================== deleteAccount ====================

    @Test
    void deleteAccount_throwsValidation_whenUserIdIsNull() {
        User transientUser = new User();
        transientUser.setUsername("transient");

        assertThatThrownBy(() -> userService.deleteAccount(transientUser))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("збережений");
    }

    @Test
    void deleteAccount_clearsDataAndDeletesUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        userService.deleteAccount(managedUser);

        // КРИТИЧНО: password_reset_token має чиститись ПЕРШИМ,
        // інакше FK-violation на userRepository.delete()
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(budgetService).clearAllUserData(managedUser);
        verify(noteService).deleteAllNotes(managedUser);
        verify(userRepository).delete(managedUser);
    }

    /**
     * Перевіряє ПОРЯДОК викликів у deleteAccount: токени → бюджет → нотатки → юзер.
     *
     * <p>Це не просто "перевірка що всі викликані" — тут важливий саме порядок,
     * бо FK-констрейнти в БД. Mockito {@code InOrder} дозволяє це перевірити.
     */
    @Test
    void deleteAccount_clearsInCorrectOrder() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(managedUser));

        userService.deleteAccount(managedUser);

        var inOrder = inOrder(passwordResetTokenRepository, budgetService, noteService, userRepository);
        inOrder.verify(passwordResetTokenRepository).deleteByUserId(1L);
        inOrder.verify(budgetService).clearAllUserData(managedUser);
        inOrder.verify(noteService).deleteAllNotes(managedUser);
        inOrder.verify(userRepository).delete(managedUser);
    }
}