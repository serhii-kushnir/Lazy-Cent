package org.lazycent.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Перевіряє контракт {@code User implements UserDetails}.
 */
class UserAuthoritiesTest {

    @Test
    void getAuthorities_prefixesRoleWithROLE() {
        User user = new User();
        user.setRole("USER");

        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactly("ROLE_USER");
    }

    @Test
    void getAuthorities_usesActualRole() {
        User user = new User();
        user.setRole("ADMIN");

        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void accountFlags_areAllTrue() {
        User user = new User();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void getUsername_returnsUsername() {
        User user = new User();
        user.setUsername("john_doe");
        assertThat(user.getUsername()).isEqualTo("john_doe");
    }

    @Test
    void getPassword_returnsPassword() {
        User user = new User();
        user.setPassword("hashed");
        assertThat(user.getPassword()).isEqualTo("hashed");
    }

    @Test
    void defaultCurrency_isUah() {
        User user = new User();
        assertThat(user.getPreferredCurrency()).isEqualTo("UAH");
    }
}