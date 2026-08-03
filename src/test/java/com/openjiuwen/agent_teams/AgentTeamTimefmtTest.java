/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.test_timefmt} in
 * {@code tests/unit_tests/agent_teams/test_timefmt.py}.
 */
class AgentTeamTimefmtTest {

    private static final long NOW_MS = 1_700_000_000_000L;
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;
    private static final Pattern OFFSET_PATTERN = Pattern.compile("[+-]\\d{2}:\\d{2}");

    @Test
    void testRelativeKeyAndValueBuckets() {
        assertRelative(-5L * SECOND, "time.just_now", null);
        assertRelative(0L, "time.just_now", null);
        assertRelative(9L * SECOND, "time.just_now", null);
        assertRelative(10L * SECOND, "time.seconds_ago", 10L);
        assertRelative(59L * SECOND, "time.seconds_ago", 59L);
        assertRelative(MINUTE, "time.minutes_ago", 1L);
        assertRelative(3599L * SECOND, "time.minutes_ago", 59L);
        assertRelative(HOUR, "time.hours_ago", 1L);
        assertRelative(86399L * SECOND, "time.hours_ago", 23L);
        assertRelative(DAY, "time.days_ago", 1L);
        assertRelative(10L * DAY, "time.days_ago", 10L);
    }

    @Test
    void testFormatTimeContextJustNowCn() {
        withLanguage("cn", () -> assertTrue(
                AgentTeamTimefmt.formatTimeContext(NOW_MS - 3L * SECOND, NOW_MS)
                        .contains(AgentTeamI18n.t("time.just_now"))
        ));
    }

    @Test
    void testFormatTimeContextMinutesAgoEn() {
        withLanguage("en", () -> assertTrue(
                AgentTeamTimefmt.formatTimeContext(NOW_MS - 3L * MINUTE, NOW_MS)
                        .contains("3m ago")
        ));
    }

    @Test
    void testFormatTimeContextHoursAndDays() {
        withLanguage("cn", () -> {
            assertTrue(AgentTeamTimefmt.formatTimeContext(NOW_MS - 2L * HOUR, NOW_MS)
                    .contains(AgentTeamI18n.t("time.hours_ago", "value", 2)));
            assertTrue(AgentTeamTimefmt.formatTimeContext(NOW_MS - 5L * DAY, NOW_MS)
                    .contains(AgentTeamI18n.t("time.days_ago", "value", 5)));
        });
    }

    @Test
    void testFormatTimeContextFutureClockSkew() {
        withLanguage("en", () -> assertTrue(
                AgentTeamTimefmt.formatTimeContext(NOW_MS + MINUTE, NOW_MS)
                        .contains("just now")
        ));
    }

    @Test
    void testFormatTimeContextNoneIsUnknown() {
        withLanguage("en", () -> assertEquals(
                "unknown time",
                AgentTeamTimefmt.formatTimeContext(null, NOW_MS)
        ));
        withLanguage("cn", () -> assertEquals(
                AgentTeamI18n.t("time.unknown"),
                AgentTeamTimefmt.formatTimeContext(null, NOW_MS)
        ));
    }

    @Test
    void testFormatTimeContextAbsoluteHasOffset() {
        withLanguage("en", () -> {
            String rendered = AgentTeamTimefmt.formatTimeContext(NOW_MS, NOW_MS);
            assertTrue(rendered.contains("2023-11-1"));
            assertTrue(OFFSET_PATTERN.matcher(rendered).find());
        });
    }

    private static void assertRelative(long deltaMs, String expectedKey, Long expectedValue) {
        AgentTeamTimefmt.RelativeKeyAndValue actual = AgentTeamTimefmt.relativeKeyAndValue(deltaMs);
        assertEquals(expectedKey, actual.key());
        if (expectedValue == null) {
            assertNull(actual.value());
            return;
        }
        assertEquals(expectedValue, actual.value());
    }

    private static void withLanguage(String language, Runnable assertion) {
        String previous = AgentTeamI18n.getLanguage();
        try {
            AgentTeamI18n.setLanguage(language);
            assertion.run();
        } finally {
            AgentTeamI18n.setLanguage(previous);
        }
    }

    @Test
    void testFormatAbsoluteContainsOffset() {
        String rendered = AgentTeamTimefmt.formatAbsolute(NOW_MS);
        assertTrue(rendered.contains("2023-11-1"));
        assertTrue(rendered.matches(".*[+-]\\d{2}:\\d{2}$"));
    }
}
