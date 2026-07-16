
package com.openjiuwen.agentteams.tools.locales;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocalesTranslatorCompatibilityTest {
    @Test
    void shouldCreateChineseTranslator() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("cn");
        assertThat(t.getLanguage()).isEqualTo("cn");
    }

    @Test
    void shouldCreateEnglishTranslator() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("en");
        assertThat(t.getLanguage()).isEqualTo("en");
    }

    @Test
    void shouldDefaultToChineseForUnknownLanguage() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("fr");
        assertThat(t.getLanguage()).isEqualTo("cn");
    }

    @Test
    void shouldDefaultToChineseForNullLanguage() {
        LocalesTranslator t = LocalesTranslator.makeTranslator(null);
        assertThat(t.getLanguage()).isEqualTo("cn");
    }

    @Test
    void shouldTranslateSendMessageParamInChinese() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("cn");
        String result = t.t("send_message", "to");
        assertThat(result).isNotEmpty();
    }

    @Test
    void shouldTranslateSendMessageParamInEnglish() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("en");
        String result = t.t("send_message", "to");
        assertThat(result).isNotEmpty();
        assertThat(result).contains("Member name");
    }

    @Test
    void shouldTranslateCreateTaskParam() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("en");
        String result = t.t("create_task", "tasks");
        assertThat(result).contains("Task list");
    }

    @Test
    void shouldFallbackToKeyIfNotFound() {
        LocalesTranslator t = LocalesTranslator.makeTranslator("en");
        String result = t.t("nonexistent_tool", "desc");
        assertThat(result).contains("nonexistent_tool");
    }
}
