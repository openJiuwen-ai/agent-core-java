package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.agent_teams.i18n}.
 */
class I18nTest {

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage("cn");
    }

    @Test
    void defaultLanguageIsCn() {
        assertEquals("cn", I18n.getLanguage().getCode());
    }

    @Test
    void setLanguageSwitchesCatalog() {
        I18n.setLanguage("en");

        assertEquals("en", I18n.getLanguage().getCode());
        assertEquals(
                "[Member Event] Member dev-1 is online",
                I18n.t("dispatcher.member_online", "target_id", "dev-1"));
    }

    @Test
    void setLanguageRejectsUnsupportedCode() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> I18n.setLanguage("fr"));

        assertTrue(error.getMessage().contains("Unsupported language 'fr'"));
    }

    @Test
    void languageFromCodeRejectsUnsupportedCode() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> I18n.Language.fromCode("fr"));

        assertTrue(error.getMessage().contains("Unsupported language 'fr'"));
    }

    @Test
    void missingI18nKeyRaisesError() {
        I18n.setLanguage("en");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> I18n.t("missing.key"));

        assertTrue(error.getMessage().contains("Missing i18n key 'missing.key'"));
    }

    @Test
    void interpolationRequiresEveryFormatKey() {
        I18n.setLanguage("en");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> I18n.t("dispatcher.member_restarted", "target_id", "dev-1"));

        assertTrue(error.getMessage().contains("Missing i18n format key 'restart_count'"));
    }

    @Test
    void tWithoutParametersReturnsRawTemplateLikePython() {
        I18n.setLanguage("en");

        assertEquals(
                "[Member Event] Member {target_id} is online",
                I18n.t("dispatcher.member_online"));
    }

    @Test
    void interpolationPreservesPythonNoneText() {
        I18n.setLanguage("en");
        Map<String, Object> params = new HashMap<>();
        params.put("target_id", null);

        assertEquals("[Member Event] Member None is online", I18n.t("dispatcher.member_online", params));
    }
}
