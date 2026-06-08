/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTeamTimefmtTest {

    private static final long NOW_MS = 1_700_000_000_000L;
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    @Test
    void testRelativeKeyAndValueBuckets() {
        assertEquals("time.just_now", AgentTeamTimefmt.relativeKeyAndValue(-5L * SECOND).key());
        assertEquals("time.just_now", AgentTeamTimefmt.relativeKeyAndValue(0L).key());
        assertEquals("time.just_now", AgentTeamTimefmt.relativeKeyAndValue(9L * SECOND).key());
        assertEquals(10L, AgentTeamTimefmt.relativeKeyAndValue(10L * SECOND).value());
        assertEquals(59L, AgentTeamTimefmt.relativeKeyAndValue(59L * SECOND).value());
        assertEquals(1L, AgentTeamTimefmt.relativeKeyAndValue(MINUTE).value());
        assertEquals(59L, AgentTeamTimefmt.relativeKeyAndValue(3599L * SECOND).value());
        assertEquals(1L, AgentTeamTimefmt.relativeKeyAndValue(HOUR).value());
        assertEquals(23L, AgentTeamTimefmt.relativeKeyAndValue(86399L * SECOND).value());
        assertEquals(1L, AgentTeamTimefmt.relativeKeyAndValue(DAY).value());
    }

    @Test
    void testFormatTimeContextUsesLocalizedStrings() {
        String previous = AgentTeamI18n.getLanguage();
        try {
            AgentTeamI18n.setLanguage("en");
            assertTrue(AgentTeamTimefmt.formatTimeContext(NOW_MS - 3L * MINUTE, NOW_MS).contains("3m ago"));
            AgentTeamI18n.setLanguage("cn");
            assertTrue(AgentTeamTimefmt.formatTimeContext(NOW_MS - 2L * HOUR, NOW_MS).contains("2"));
            assertEquals(AgentTeamI18n.t("time.unknown"), AgentTeamTimefmt.formatTimeContext(null, NOW_MS));
        } finally {
            AgentTeamI18n.setLanguage(previous);
        }
    }

    @Test
    void testFormatAbsoluteContainsOffset() {
        String rendered = AgentTeamTimefmt.formatAbsolute(NOW_MS);
        assertTrue(rendered.contains("2023-11-1"));
        assertNotNull(rendered);
        assertTrue(rendered.matches(".*[+-]\\d{2}:\\d{2}$"));
    }
}
