package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.User;
import org.lazycent.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_returnsUser_whenFound() {
        User user = new User();
        user.setId(1L);
        user.setUsername("john");
        user.setPassword("hashed");
        user.setRole("USER");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("john");

        assertThat(result).isSameAs(user);
        assertThat(result.getUsername()).isEqualTo("john");
        assertThat(result.getPassword()).isEqualTo("hashed");
    }

    @Test
    void loadUserByUsername_throws_whenNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}