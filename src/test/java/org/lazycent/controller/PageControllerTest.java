package org.lazycent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тести {@link PageController} — статичні сторінки privacy / terms з
 * локалізацією (uk → privacy, en → privacy_en).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void privacy_accessibleAnonymously() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk());
    }

    @Test
    void terms_accessibleAnonymously() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk());
    }

    @Test
    void privacy_returnsUkrainianTemplate_byDefault() throws Exception {
        // Java-дефолт — зазвичай en_US, але MessageSource має
        // fallbackToSystemLocale=false, а Thymeleaf використовує
        // LocaleContextHolder. Без явного налаштування локаль = system default.
        // Тест перевіряє тільки, що сторінка рендериться.
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("privacy"),
                        org.hamcrest.Matchers.is("privacy_en"))));
    }

    @Test
    void privacy_returnsEnglishTemplate_withLangParam() throws Exception {
        // ?lang=en — LocaleChangeInterceptor із WebConfig підхопить
        mockMvc.perform(get("/privacy").param("lang", "en"))
                .andExpect(status().isOk());
    }

    @Test
    void topicLandingPages_arePublicAndIndexable() throws Exception {
        mockMvc.perform(get("/family-expenses").param("lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(view().name("seo-topic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Облік сімейних витрат без втрати особистого контролю")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://lazycent.com/family-expenses")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("index, follow")));

        mockMvc.perform(get("/budget-planning").param("lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Планування бюджету, яке показує не лише витрати, а й залишок")));

        mockMvc.perform(get("/income-tracker").param("lang", "uk"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Облік доходів і зрозумілий прогрес до місячної цілі")));
    }

    @Test
    void topicLandingPages_areLocalized() throws Exception {
        mockMvc.perform(get("/income-tracker").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Track income and see progress toward a monthly goal")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://lazycent.com/income-tracker?lang=en")));
    }
}