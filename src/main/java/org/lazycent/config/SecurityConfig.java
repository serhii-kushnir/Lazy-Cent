package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import org.lazycent.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final LocaleAwareAuthenticationSuccessHandler successHandler;
    private final RateLimitFilter rateLimitFilter;
    private final JsonAwareAuthenticationEntryPoint jsonAwareAuthenticationEntryPoint;  // ← ДОДАНО

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Rate limit перед формою логіну
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                // Дозволи на URL
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login", "/register",
                                "/lib/**", "/css/**", "/img/**", "/js/**", "/webjars/**",
                                "/favicon.ico", "/logo.png",
                                "/privacy", "/terms", "/error", "/error/**",
                                "/forgot-password", "/reset-password",
                                "/actuator/health", "/actuator/health/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // ===== ENTRY POINT: JSON для AJAX, redirect для HTML =====
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAwareAuthenticationEntryPoint)
                )

                // ===== FORM LOGIN =====
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .permitAll()
                )

                // ===== OAUTH2 LOGIN =====
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(successHandler)
                )

                // ===== LOGOUT =====
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }
}