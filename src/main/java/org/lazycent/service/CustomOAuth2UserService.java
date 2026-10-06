package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = fetchOAuth2User(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId();

        String providerId = extractProviderId(attributes);

        if (providerId == null) {
            throw new OAuth2AuthenticationException("Провайдер не повернув ідентифікатор користувача");
        }

        String email = attributes.get("email") != null
                ? attributes.get("email").toString()
                : null;

        String name = attributes.get("name") != null
                ? attributes.get("name").toString()
                : null;

        log.debug("OAuth2 login: provider={}, providerId={}, email={}, name={}",
                provider, providerId, email, name);

        User user = userService.saveOrUpdateOAuth2User(provider, providerId, email, name);

        return new CustomOAuth2User(oAuth2User, user);
    }

    /**
     * Викликає реальний OAuth2 endpoint провайдера.
     *
     * <p><b>Навіщо окремий {@code protected}-метод:</b> щоб у тестах
     * підмінити його без реального HTTP-виклику. {@code DefaultOAuth2UserService.loadUser()}
     * робить REST-запит, який у unit-тесті недоступний.
     */
    protected OAuth2User fetchOAuth2User(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }

    /**
     * Витягує providerId з атрибутів провайдера.
     * Google (OIDC) → {@code sub}; GitHub → {@code id}.
     */
    private String extractProviderId(Map<String, Object> attributes) {
        if (attributes.get("sub") != null) {
            return attributes.get("sub").toString();
        }
        if (attributes.get("id") != null) {
            return attributes.get("id").toString();
        }
        return null;
    }
}