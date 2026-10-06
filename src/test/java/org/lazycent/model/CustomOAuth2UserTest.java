package org.lazycent.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomOAuth2UserTest {

    private OAuth2User oAuth2User;
    private User user;
    private CustomOAuth2User customOAuth2User;

    @BeforeEach
    void setUp() {
        oAuth2User = mock(OAuth2User.class);
        when(oAuth2User.getAttributes()).thenReturn(Map.of("sub", "12345", "email", "u@example.com"));

        user = new User();
        user.setId(1L);
        user.setUsername("oauth_user");
        user.setRole("USER");
        user.setProvider("google");
        user.setProviderId("12345");

        customOAuth2User = new CustomOAuth2User(oAuth2User, user);
    }

    @Test
    void getAttributes_delegatesToWrappedUser() {
        Map<String, Object> attrs = customOAuth2User.getAttributes();
        assertThat(attrs).containsEntry("sub", "12345");
        assertThat(attrs).containsEntry("email", "u@example.com");
    }

    @Test
    void getAuthorities_returnsUserAuthorities() {
        Collection<? extends GrantedAuthority> authorities = customOAuth2User.getAuthorities();
        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void getName_returnsUsername() {
        assertThat(customOAuth2User.getName()).isEqualTo("oauth_user");
    }

    @Test
    void getUser_returnsWrappedEntity() {
        assertThat(customOAuth2User.getUser()).isSameAs(user);
    }
}