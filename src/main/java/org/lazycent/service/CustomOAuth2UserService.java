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
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId();

        // providerId: спочатку "sub" (Google, OIDC), потім "id" (GitHub і деякі інші)
        String providerId = attributes.get("sub") != null
                ? attributes.get("sub").toString()
                : attributes.get("id") != null
                  ? attributes.get("id").toString()
                  : null;

        if (providerId == null) {
            throw new OAuth2AuthenticationException("Провайдер не повернув ідентифікатор користувача");
        }

        // Email може бути відсутнім — залишаємо null, НЕ створюємо фейковий
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
}