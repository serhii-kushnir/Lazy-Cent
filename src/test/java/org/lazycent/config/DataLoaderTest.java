package org.lazycent.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.DayBudgetRepository;
import org.lazycent.repository.UserRepository;
import org.lazycent.service.DemoDataService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DataLoaderTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private DemoDataService demoDataService;
    private DayBudgetRepository dayBudgetRepository;
    private DataLoader loader;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        demoDataService = mock(DemoDataService.class);
        dayBudgetRepository = mock(DayBudgetRepository.class);

        loader = new DataLoader(userRepository, passwordEncoder, demoDataService, dayBudgetRepository);

        ReflectionTestUtils.setField(loader, "defaultUsername", "demo");
        ReflectionTestUtils.setField(loader, "defaultPassword", "secret123");
        ReflectionTestUtils.setField(loader, "randomDays", 14);
    }

    @Test
    void run_throwsIllegalState_whenPasswordBlank() {
        ReflectionTestUtils.setField(loader, "defaultPassword", "");

        assertThatThrownBy(() -> loader.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("default-password");

        verifyNoInteractions(userRepository, demoDataService);
    }

    @Test
    void run_throwsIllegalState_whenPasswordNull() {
        ReflectionTestUtils.setField(loader, "defaultPassword", null);

        assertThatThrownBy(() -> loader.run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void run_createsNewUser_whenNotExists() {
        when(userRepository.findByUsername("demo")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        loader.run();

        verify(userRepository).save(any(User.class));
        verify(demoDataService).createDefaultCategories(any(User.class));
    }

    @Test
    void run_generatesDemoTransactions_whenNoDays() {
        User existing = new User();
        existing.setId(1L);
        existing.setUsername("demo");

        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(existing));
        when(dayBudgetRepository.countByOwner(existing)).thenReturn(0L);

        loader.run();

        verify(demoDataService).createDefaultCategories(existing);
        verify(demoDataService).createRandomTransactions(existing, 14);
    }

    @Test
    void run_skipsDemoTransactions_whenDaysExist() {
        User existing = new User();
        existing.setId(1L);
        existing.setUsername("demo");

        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(existing));
        when(dayBudgetRepository.countByOwner(existing)).thenReturn(5L);

        loader.run();

        verify(demoDataService).createDefaultCategories(existing);
        verify(demoDataService, never()).createRandomTransactions(any(), anyInt());
    }
}