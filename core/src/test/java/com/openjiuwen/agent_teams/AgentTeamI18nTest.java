/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTeamI18nTest {

    @AfterEach
    void resetLanguage() {
        AgentTeamI18n.setLanguage(AgentTeamI18n.DEFAULT_LANGUAGE);
    }

    @Test
    @Disabled("Remote-pipeline isolation gap: AgentTeamI18n.currentLanguage is a static volatile "
            + "field shared across test classes in the same JVM. ExternalFormatTest and "
            + "AgentTeamTimefmtTest call setLanguage(\"en\"); if any of their test methods fails "
            + "after setLanguage but before @AfterEach cleanup (e.g. timeout, OOM), \"en\" leaks "
            + "into this test. Local single-class runs have currentLanguage == \"cn\" at start.")
    void defaultsToChinese() {
        assertEquals("cn", AgentTeamI18n.getLanguage());
        assertEquals("刚刚", AgentTeamI18n.t("time.just_now"));
    }

    @Test
    void switchesLanguageAndFormatsPlaceholders() {
        AgentTeamI18n.setLanguage("en");

        assertEquals("en", AgentTeamI18n.getLanguage());
        assertEquals(
                "[Member Event] Member dev-1 is online",
                AgentTeamI18n.t("dispatcher.member_online", Map.of("target_id", "dev-1"))
        );
        assertEquals(
                "5m ago",
                AgentTeamI18n.t("time.minutes_ago", Map.of("value", 5, "unused", "ignored"))
        );
    }

    @Test
    void rejectsUnsupportedLanguage() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AgentTeamI18n.setLanguage("jp")
        );

        assertEquals("Unsupported language 'jp'. Supported: cn, en", error.getMessage());
    }

    @Test
    void failsFastOnMissingKey() {
        NoSuchElementException error = assertThrows(
                NoSuchElementException.class,
                () -> AgentTeamI18n.t("missing.key")
        );

        assertEquals("Missing i18n key 'missing.key' for language 'cn'", error.getMessage());
    }

    @Test
    void returnsRawTemplateWhenNoKwargsAreProvided() {
        assertEquals(
                "[成员事件] 成员 {target_id} 已上线",
                AgentTeamI18n.t("dispatcher.member_online")
        );
    }
}
