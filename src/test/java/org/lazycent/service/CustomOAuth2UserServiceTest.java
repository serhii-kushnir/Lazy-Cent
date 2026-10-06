package org.lazycent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тести {@link CustomOAuth2UserService}.
 *
 * <p>Реальний {@code super.loadUser()} вимагає HTTP-виклику до OAuth2-
 * провайдера, тому в тестах підмінюємо {@code fetchOAuth2User(...)}.
 */
class CustomOAuth2UserServiceTest {

    private UserService userService;
    private TestableOAuth2UserService service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        service = new TestableOAuth2UserService(userService);
    }

    // ==================== HAPPY PATH ====================

    @Test
    void loadUser_extractsProviderIdFromSub_googleStyle() {
        User saved = new User();
        saved.setId(1L);
        saved.setUsername("google_user");
        when(userService.saveOrUpdateOAuth2User(eq("google"), eq("sub-123"), any(), any()))
                .thenReturn(saved);

        service.setStubOAuth2User(oauthUser(Map.of(
                "sub", "sub-123",
                "email", "u@example.com",
                "name", "Google User"
        )));

        OAuth2User result = service.loadUser(request("google"));

        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) result).getUser()).isSameAs(saved);
        verify(userService).saveOrUpdateOAuth2User("google", "sub-123", "u@example.com", "Google User");
    }

    @Test
    void loadUser_extractsProviderIdFromId_githubStyle() {
        User saved = new User();
        saved.setId(2L);
        saved.setUsername("gh_user");
        when(userService.saveOrUpdateOAuth2User(eq("github"), eq("999"), any(), any()))
                .thenReturn(saved);

        service.setStubOAuth2User(oauthUser(Map.of(
                "id", "999",
                "email", "gh@example.com",
                "name", "GH User"
        )));

        OAuth2User result = service.loadUser(request("github"));

        assertThat(((CustomOAuth2User) result).getUser()).isSameAs(saved);
        verify(userService).saveOrUpdateOAuth2User("github", "999", "gh@example.com", "GH User");
    }

    @Test
    void loadUser_handlesMissingEmail() {
        User saved = new User();
        saved.setId(3L);
        saved.setUsername("no_email_user");
        when(userService.saveOrUpdateOAuth2User(eq("github"), eq("777"), eq(null), any()))
                .thenReturn(saved);

        service.setStubOAuth2User(oauthUser(Map.of(
                "id", "777",
                "name", "No Email"
        )));

        OAuth2User result = service.loadUser(request("github"));

        assertThat(result).isNotNull();
        verify(userService).saveOrUpdateOAuth2User("github", "777", null, "No Email");
    }

    @Test
    void loadUser_handlesMissingName() {
        User saved = new User();
        saved.setId(4L);
        saved.setUsername("unnamed_user");
        when(userService.saveOrUpdateOAuth2User(eq("google"), eq("sub-x"), any(), eq(null)))
                .thenReturn(saved);

        service.setStubOAuth2User(oauthUser(Map.of(
                "sub", "sub-x",
                "email", "x@example.com"
        )));

        OAuth2User result = service.loadUser(request("google"));

        assertThat(result).isNotNull();
        verify(userService).saveOrUpdateOAuth2User("google", "sub-x", "x@example.com", null);
    }

    // ==================== ERROR PATH ====================

    @Test
    void loadUser_throws_whenNoProviderId() {
        service.setStubOAuth2User(oauthUser(Map.of(
                "email", "u@example.com",
                "name", "Anonymous"
        )));

        assertThatThrownBy(() -> service.loadUser(request("google")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oae = (OAuth2AuthenticationException) ex;
                    assertThat(oae.getError().getErrorCode())
                            .as("errorCode має містити пояснення про ідентифікатор")
                            .contains("ідентифікатор");
                });
    }

    @Test
    void loadUser_prefersSubOverId() {
        User saved = new User();
        saved.setId(5L);
        saved.setUsername("u");
        when(userService.saveOrUpdateOAuth2User(eq("google"), eq("sub-wins"), any(), any()))
                .thenReturn(saved);

        service.setStubOAuth2User(oauthUser(Map.of(
                "sub", "sub-wins",
                "id", "id-loses",
                "email", "u@example.com"
        )));

        service.loadUser(request("google"));

        verify(userService).saveOrUpdateOAuth2User("google", "sub-wins", "u@example.com", null);
    }

    // ==================== ДОПОМІЖНІ ====================

    private OAuth2User oauthUser(Map<String, Object> attrs) {
        // DefaultOAuth2User вимагає, щоб userNameAttributeName був у attrs.
        // Для GitHub-кейсів (id замість sub) це не працює, тому мокаємо.
        OAuth2User mockUser = mock(OAuth2User.class);
        when(mockUser.getAttributes()).thenReturn(attrs);
        return mockUser;
    }

    private OAuth2UserRequest request(String registrationId) {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("secret")
                .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .userInfoUri("https://example.com/oauth2/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600));

        return new OAuth2UserRequest(registration, token);
    }

    /**
     * Підклас, що підмінює {@code fetchOAuth2User} — щоб не робити
     * реального HTTP-виклику до OAuth2-провайдера.
     */
    private static class TestableOAuth2UserService extends CustomOAuth2UserService {
        private OAuth2User stubOAuth2User;

        TestableOAuth2UserService(UserService userService) {
            super(userService);
        }

        void setStubOAuth2User(OAuth2User user) {
            this.stubOAuth2User = user;
        }

        @Override
        protected OAuth2User fetchOAuth2User(OAuth2UserRequest req) {
            return stubOAuth2User;
        }
    }
}