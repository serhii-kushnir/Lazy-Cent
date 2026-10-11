package org.lazycent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.exception.ValidationException;
import org.lazycent.model.User;
import org.lazycent.repository.PasswordResetTokenRepository;
import org.lazycent.repository.UserRepository;
import org.mockito.ArgumentCaptor;
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

/**
 * Доповнює {@code UserServiceTest}: гілки {@code saveOrUpdateOAuth2User},
 * створення нового OAuth2-користувача та дублікати при {@code registerUser}.
 */
class UserServiceOAuth2Test {

    private UserRepository userRepository;
    private DemoDataService demoDataService;
    private PasswordEncoder passwordEncoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk-UA"));

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);

        userRepository = mock(UserRepository.class);
        demoDataService = mock(DemoDataService.class);
        passwordEncoder = new BCryptPasswordEncoder();

        service = new UserService(
                userRepository,
                mock(PasswordResetTokenRepository.class),
                passwordEncoder,
                demoDataService,
                mock(BudgetService.class),
                mock(NoteService.class),
                messageSource);

        when(userRepository.findByProviderAndProviderId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(100L);
            return u;
        });
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private User existingOAuthUser() {
        User u = new User();
        u.setId(5L);
        u.setUsername("Old Name");
        u.setEmail("old@example.com");
        u.setProvider("google");
        u.setProviderId("g-1");
        return u;
    }

    // ==================== ІСНУЮЧИЙ ЗА (provider, providerId) ====================

    @Test
    void existing_unchangedData_returnsSameUser_withoutSave() {
        User existing = existingOAuthUser();
        when(userRepository.findByProviderAndProviderId("google", "g-1")).thenReturn(Optional.of(existing));

        User result = service.saveOrUpdateOAuth2User("google", "g-1", "old@example.com", "Old Name");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void existing_changedNameAndEmail_areUpdatedAndSaved() {
        User existing = existingOAuthUser();
        when(userRepository.findByProviderAndProviderId("google", "g-1")).thenReturn(Optional.of(existing));

        User result = service.saveOrUpdateOAuth2User("google", "g-1", "new@example.com", "New Name");

        assertThat(result.getUsername()).isEqualTo("New Name");
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(existing);
    }

    @Test
    void existing_nullOrEmptyNameAndEmail_keepOldValues() {
        User existing = existingOAuthUser();
        when(userRepository.findByProviderAndProviderId("google", "g-1")).thenReturn(Optional.of(existing));

        User result = service.saveOrUpdateOAuth2User("google", "g-1", "", null);

        assertThat(result.getUsername()).isEqualTo("Old Name");
        assertThat(result.getEmail()).isEqualTo("old@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== ЗВ'ЯЗУВАННЯ ЗА EMAIL ====================

    @Test
    void sameEmail_localAccountWithoutProvider_isLinked() {
        User local = new User();
        local.setId(7L);
        local.setUsername("local");
        local.setEmail("shared@example.com");
        when(userRepository.findByEmail("shared@example.com")).thenReturn(Optional.of(local));

        User result = service.saveOrUpdateOAuth2User("google", "g-9", "shared@example.com", "Google Name");

        assertThat(result).isSameAs(local);
        assertThat(result.getProvider()).isEqualTo("google");
        assertThat(result.getProviderId()).isEqualTo("g-9");
        assertThat(result.getUsername()).isEqualTo("Google Name");
        verify(demoDataService, never()).createDefaultCategories(any());
    }

    @Test
    void sameEmail_localAccountWithEmptyProvider_isLinked_keepsUsernameWhenNameNull() {
        User local = new User();
        local.setId(7L);
        local.setUsername("local");
        local.setProvider("");
        when(userRepository.findByEmail("shared@example.com")).thenReturn(Optional.of(local));

        User result = service.saveOrUpdateOAuth2User("google", "g-9", "shared@example.com", null);

        assertThat(result.getProvider()).isEqualTo("google");
        assertThat(result.getUsername()).isEqualTo("local");
    }

    @Test
    void sameEmail_sameProviderNewProviderId_updatesProviderId() {
        User byEmail = existingOAuthUser();
        when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(byEmail));

        User result = service.saveOrUpdateOAuth2User("google", "g-NEW", "old@example.com", "Old Name");

        assertThat(result).isSameAs(byEmail);
        assertThat(result.getProviderId()).isEqualTo("g-NEW");
        verify(userRepository).save(byEmail);
    }

    @Test
    void sameEmail_differentProvider_createsNewUserWithoutEmail() {
        User github = existingOAuthUser();
        github.setProvider("github");
        when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(github));

        User result = service.saveOrUpdateOAuth2User("google", "g-2", "old@example.com", "Google Person");

        assertThat(result).isNotSameAs(github);
        assertThat(result.getEmail()).isNull();
        assertThat(result.getProvider()).isEqualTo("google");
        assertThat(result.getProviderId()).isEqualTo("g-2");
        verify(demoDataService).createDefaultCategories(result);
    }

    // ==================== НОВИЙ КОРИСТУВАЧ ====================

    @Test
    void newUser_setsDefaults_andCreatesCategories() {
        User result = service.saveOrUpdateOAuth2User("google", "g-3", "n@example.com", "Nova");

        assertThat(result.getUsername()).isEqualTo("Nova");
        assertThat(result.getEmail()).isEqualTo("n@example.com");
        assertThat(result.getRole()).isEqualTo("USER");
        assertThat(result.getPreferredCurrency()).isEqualTo("UAH");
        verify(demoDataService).createDefaultCategories(result);
    }

    @Test
    void newUser_getsRandomEncodedPassword() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        service.saveOrUpdateOAuth2User("google", "g-3", "n@example.com", "Nova");
        service.saveOrUpdateOAuth2User("google", "g-4", "m@example.com", "Mova");

        verify(userRepository, times(2)).save(captor.capture());
        String p1 = captor.getAllValues().get(0).getPassword();
        String p2 = captor.getAllValues().get(1).getPassword();
        assertThat(p1).isNotBlank().startsWith("$2");
        assertThat(p1).isNotEqualTo(p2);
    }

    @Test
    void newUser_withoutName_usesEmailAsUsername() {
        User result = service.saveOrUpdateOAuth2User("github", "42", "only@example.com", null);

        assertThat(result.getUsername()).isEqualTo("only@example.com");
    }

    @Test
    void newUser_withoutNameAndEmail_usesProviderAndId() {
        User result = service.saveOrUpdateOAuth2User("github", "42", null, null);

        assertThat(result.getUsername()).isEqualTo("github_42");
        assertThat(result.getEmail()).isNull();
    }

    @Test
    void newUser_emptyEmail_isStoredAsNull() {
        User result = service.saveOrUpdateOAuth2User("github", "43", "", "Name");

        assertThat(result.getEmail()).isNull();
    }

    // ==================== registerUser: дублікати ====================

    @Test
    void register_duplicateUsername_throwsValidation_andSavesNothing() {
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.registerUser("taken", "Password1!", "a@example.com"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("вже існує");

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(demoDataService);
    }

    @Test
    void register_duplicateEmail_throwsValidation_andSavesNothing() {
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.registerUser("fresh", "Password1!", "dup@example.com"))
                .isInstanceOf(ValidationException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_emptyOrNullEmail_skipsEmailLookup() {
        service.registerUser("u1", "Password1!", "");
        service.registerUser("u2", "Password1!", null);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void register_encodesPassword_setsDefaults_andCreatesCategories() {
        User result = service.registerUser("fresh", "Password1!", "f@example.com");

        assertThat(result.getPassword()).isNotEqualTo("Password1!");
        assertThat(passwordEncoder.matches("Password1!", result.getPassword())).isTrue();
        assertThat(result.getRole()).isEqualTo("USER");
        assertThat(result.getPreferredCurrency()).isEqualTo("UAH");
        verify(demoDataService).createDefaultCategories(result);
    }
}