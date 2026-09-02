
package com.openjiuwen.agentteams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class I18nCompatibilityTest {
    @AfterEach
    void resetLanguage() {
        I18n.setLanguage(I18n.Language.CN);
    }

    @Test
    void shouldDefaultToChinese() {
        assertThat(I18n.getLanguage()).isEqualTo(I18n.Language.CN);
    }

    @Test
    void shouldSwitchLanguage() {
        I18n.setLanguage(I18n.Language.EN);
        assertThat(I18n.getLanguage()).isEqualTo(I18n.Language.EN);
    }

    @Test
    void shouldTranslateToChinese() {
        String msg = I18n.t("dispatcher.member_online", "dev-1");
        assertThat(msg).contains("dev-1");
    }

    @Test
    void shouldTranslateToEnglish() {
        I18n.setLanguage(I18n.Language.EN);
        String msg = I18n.t("dispatcher.member_online", "dev-1");
        assertThat(msg).contains("dev-1");
        assertThat(msg).contains("Member Event");
    }

    @Test
    void shouldReturnDefaultPersonaInChinese() {
        String persona = I18n.t("blueprint.default_persona");
        assertThat(persona).isNotEmpty();
    }

    @Test
    void shouldTranslateStaleClaimMessage() {
        String msg = I18n.t("dispatcher.stale_claim_header", "3");
        assertThat(msg).contains("3");
    }

    @Test
    void shouldThrowForMissingKey() {
        assertThatThrownBy(() -> I18n.t("nonexistent.key")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForNullLanguage() {
        assertThatThrownBy(() -> I18n.setLanguage(null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldTranslateAllDoneMessage() {
        I18n.setLanguage(I18n.Language.EN);
        String msg = I18n.t("dispatcher.all_done_temporary");
        assertThat(msg).contains("clean_team");
    }
}
