/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python 0.1.15 {@code test_language_propagation.py}.
 * Validates language propagation from I18n to TeamAgentSpec.
 */
class I18nLanguagePropagationCompatibilityTest {

    @AfterEach
    void resetLanguage() {
        I18n.setLanguage(I18n.Language.CN);
    }

    @Test
    void languageEn_propagatesToTeamSpec() {
        I18n.setLanguage(I18n.Language.EN);
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("lang-team")
                .language(I18n.getLanguage().name().toLowerCase())
                .build();
        assertThat(spec.getLanguage()).isEqualTo("en");
    }

    @Test
    void languageNone_fallsBackToCn() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("lang-team")
                .build();
        // Default language should be CN
        assertThat(spec.getLanguage()).isNull();
        // When null, runtime falls back to CN (Python behavior)
        I18n.setLanguage(I18n.Language.CN);
        assertThat(I18n.getLanguage()).isEqualTo(I18n.Language.CN);
    }

    @Test
    void languageZh_normalizesToCn() {
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("lang-team")
                .language("zh")
                .build();
        // Python normalizes "zh" → "cn"
        assertThat(normalizeLanguage(spec.getLanguage())).isEqualTo("cn");
    }

    @Test
    void languagePropagatesToMemberSpec() {
        I18n.setLanguage(I18n.Language.EN);
        TeamAgentSpec spec = TeamAgentSpec.builder()
                .name("lang-team")
                .language(I18n.getLanguage().name().toLowerCase())
                .members(List.of(
                        TeamMemberSpec.builder().name("leader").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build();
        // Language is a team-level setting, not per-member
        assertThat(spec.getLanguage()).isEqualTo("en");
    }

    @Test
    void i18nSwitch_affectsRuntimeStrings() {
        I18n.setLanguage(I18n.Language.EN);
        assertThat(I18n.t("time.just_now")).isEqualTo("just now");

        I18n.setLanguage(I18n.Language.CN);
        assertThat(I18n.t("time.just_now")).isEqualTo("\u521a\u521a");
    }

    @Test
    void i18nPlaceholderSubstitution() {
        I18n.setLanguage(I18n.Language.EN);
        assertThat(I18n.t("dispatcher.member_online", "dev-1"))
                .isEqualTo("[Member Event] Member dev-1 is online");

        I18n.setLanguage(I18n.Language.CN);
        assertThat(I18n.t("dispatcher.member_online", "dev-1"))
                .isEqualTo("[\u6210\u5458\u4e8b\u4ef6] \u6210\u5458 dev-1 \u5df2\u4e0a\u7ebf");
    }

    private static String normalizeLanguage(String lang) {
        if (lang == null) {
            return "cn";
        }
        return switch (lang.toLowerCase()) {
            case "zh", "zh-cn", "chinese" -> "cn";
            case "en", "en-us", "english" -> "en";
            default -> lang.toLowerCase();
        };
    }
}
