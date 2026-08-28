// service/CustomOAuth2UserService.java (повний код, що використовує CustomOAuth2User)
package org.lazycent.service;

import lombok.RequiredArgsConstructor;
import org.lazycent.model.CustomOAuth2User;
import org.lazycent.model.User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = attributes.get("sub").toString();
        String email = attributes.get("email") != null ? attributes.get("email").toString() : null;
        String name = attributes.get("name") != null ? attributes.get("name").toString() : null;

        // Якщо email відсутній, використовуємо providerId як унікальний ідентифікатор
        if (email == null) {
            email = provider + "_" + providerId;
        }

        User user = userService.saveOAuth2User(provider, providerId, email, name);

        // Повертаємо CustomOAuth2User, який об'єднує OAuth2User та нашого User
        return new CustomOAuth2User(oAuth2User, user);
    }
}