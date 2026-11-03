/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.timefmt;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.I18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

/**
 * Mirrors Python 0.1.15 {@code test_timefmt.py}.
 * Validates bucket selection (pure numeric) and localized rendering.
 */
class TimeFormatCompatibilityTest {

    // Fixed anchor: 2023-11-14 22:13:20 UTC
    private static final long NOW_MS = 1_700_000_000_000L;

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private I18n.Language savedLanguage;

    @BeforeEach
    void saveLanguage() {
        savedLanguage = I18n.getLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        I18n.setLanguage(savedLanguage);
    }

    // --- Bucket selection: pure numeric, language-independent ---

    @Test
    void justNowBucket_negativeDelta() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - (-5 * SECOND), NOW_MS);
        assertThat(result).contains("just now");
    }

    @Test
    void justNowBucket_zeroDelta() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS, NOW_MS);
        assertThat(result).contains("just now");
    }

    @Test
    void justNowBucket_under10Seconds() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 9 * SECOND, NOW_MS);
        assertThat(result).contains("just now");
    }

    @Test
    void secondsAgoBucket_10Seconds() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 10 * SECOND, NOW_MS);
        assertThat(result).contains("10s ago");
    }

    @Test
    void secondsAgoBucket_59Seconds() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 59 * SECOND, NOW_MS);
        assertThat(result).contains("59s ago");
    }

    @Test
    void minutesAgoBucket_1Minute() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - MINUTE, NOW_MS);
        assertThat(result).contains("1m ago");
    }

    @Test
    void minutesAgoBucket_59Minutes() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 3599 * SECOND, NOW_MS);
        assertThat(result).contains("59m ago");
    }

    @Test
    void hoursAgoBucket_1Hour() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - HOUR, NOW_MS);
        assertThat(result).contains("1h ago");
    }

    @Test
    void hoursAgoBucket_23Hours() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 86399 * SECOND, NOW_MS);
        assertThat(result).contains("23h ago");
    }

    @Test
    void daysAgoBucket_1Day() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - DAY, NOW_MS);
        assertThat(result).contains("1d ago");
    }

    @Test
    void daysAgoBucket_10Days() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 10 * DAY, NOW_MS);
        assertThat(result).contains("10d ago");
    }

    // --- Localized rendering ---

    @Test
    void justNowCn() {
        I18n.setLanguage(I18n.Language.CN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 3 * SECOND, NOW_MS);
        assertThat(result).contains("\u521a\u521a"); // 刚刚
    }

    @Test
    void minutesAgoEn() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS - 3 * MINUTE, NOW_MS);
        assertThat(result).contains("3m ago");
    }

    @Test
    void hoursAndDaysCn() {
        I18n.setLanguage(I18n.Language.CN);
        String hours = TimeFormat.formatTimeContext(NOW_MS - 2 * HOUR, NOW_MS);
        assertThat(hours).contains("2 \u5c0f\u65f6\u524d"); // 2 小时前

        String days = TimeFormat.formatTimeContext(NOW_MS - 5 * DAY, NOW_MS);
        assertThat(days).contains("5 \u5929\u524d"); // 5 天前
    }

    // --- Edge cases ---

    @Test
    void futureClockSkewRendersJustNow() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS + MINUTE, NOW_MS);
        assertThat(result).contains("just now");
    }

    @Test
    void nullTimestampRendersUnknownEn() {
        I18n.setLanguage(I18n.Language.EN);
        assertThat(TimeFormat.formatTimeContext(null, NOW_MS)).isEqualTo("unknown time");
    }

    @Test
    void nullTimestampRendersUnknownCn() {
        I18n.setLanguage(I18n.Language.CN);
        assertThat(TimeFormat.formatTimeContext(null, NOW_MS)).isEqualTo("\u65f6\u95f4\u672a\u77e5"); // 时间未知
    }

    @Test
    void absoluteHasDateAndOffset() {
        I18n.setLanguage(I18n.Language.EN);
        String result = TimeFormat.formatTimeContext(NOW_MS, NOW_MS);
        // Date part stable (2023-11-14 UTC, local may shift day)
        assertThat(result).containsPattern(Pattern.compile("2023-11-1[45]"));
        // Timezone offset ±HH:MM
        assertThat(result).containsPattern(Pattern.compile("[+-]\\d{2}:\\d{2}"));
    }
}
