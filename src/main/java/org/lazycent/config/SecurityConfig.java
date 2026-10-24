package org.lazycent.config;

import lombok.RequiredArgsConstructor;
import org.lazycent.service.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final LocaleAwareAuthenticationSuccessHandler successHandler;
    private final RateLimitFilter rateLimitFilter;
    private final JsonAwareAuthenticationEntryPoint jsonAwareAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login", "/register",
                                "/lib/**", "/css/**", "/img/**", "/js/**", "/webjars/**",
                                "/favicon.ico", "/logo.png",
                                "/privacy", "/terms", "/error", "/error/**",
                                "/forgot-password", "/reset-password",
                                "/actuator/health", "/robots.txt", "/sitemap.xml", "/actuator/health/**",
                                "/manifest.json",
                                "/sw.js",
                                "/video/**"
                        ).permitAll()
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )

                // 👇 ДОДАЙТЕ ЦІ ДВА БЛОКИ ДЛЯ H2 CONSOLE 👇
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                // 👆 ------------------------------------ 👆

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jsonAwareAuthenticationEntryPoint)
                )

                // ===== SESSION MANAGEMENT =====
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(3)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login?expired")
                        .sessionRegistry(sessionRegistry())
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .permitAll()
                )

                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(successHandler)
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("LAZYCENT_SESSION")
                        .permitAll()
                );

        return http.build();
    }
    /**
     * Реєстр сесій для {@code maximumSessions(3)}.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Обов'язковий бін для коректного трекінгу сесій.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}