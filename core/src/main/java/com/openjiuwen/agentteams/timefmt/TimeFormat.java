/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.timefmt;

import com.openjiuwen.agentteams.I18n;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Human-readable time rendering for agent-facing team text.
 *
 * <p>Mirrors Python {@code timefmt.py}. Turns a millisecond UTC epoch into
 * {@code "<absolute local time> (<relative diff>)"} so an LLM can reason about
 * recency without parsing a bare integer. Bucket logic is pure numeric; only
 * the wording lives in {@link I18n}.
 *
 * @since 2026/7/9
 */
public final class TimeFormat {
    private static final long JUST_NOW_SECONDS = 10L;
    private static final long MINUTE_SECONDS = 60L;
    private static final long HOUR_SECONDS = 60L * 60;
    private static final long DAY_SECONDS = 24L * 60 * 60;

    private static final DateTimeFormatter ABSOLUTE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OFFSET_FORMAT =
            DateTimeFormatter.ofPattern("XXX");

    private TimeFormat() {
    }

    /**
     * Render a timestamp as {@code "<absolute local time> (<relative diff>)"}.
     *
     * @param timestampMs millisecond UTC epoch, or {@code null} when unset
     * @param nowMs current millisecond UTC epoch (relative anchor)
     * @return localized text, or {@code I18n.t("time.unknown")} when timestamp is null
     */
    public static String formatTimeContext(Long timestampMs, long nowMs) {
        if (timestampMs == null) {
            return I18n.t("time.unknown");
        }
        long deltaMs = nowMs - timestampMs;
        String relative = renderRelative(deltaMs);
        return formatAbsolute(timestampMs) + " (" + relative + ")";
    }

    private static String renderRelative(long deltaMs) {
        if (deltaMs < 0) {
            return I18n.t("time.just_now");
        }
        long seconds = deltaMs / 1000;
        if (seconds < JUST_NOW_SECONDS) {
            return I18n.t("time.just_now");
        }
        if (seconds < MINUTE_SECONDS) {
            return I18n.t("time.seconds_ago", seconds);
        }
        if (seconds < HOUR_SECONDS) {
            return I18n.t("time.minutes_ago", seconds / MINUTE_SECONDS);
        }
        if (seconds < DAY_SECONDS) {
            return I18n.t("time.hours_ago", seconds / HOUR_SECONDS);
        }
        return I18n.t("time.days_ago", seconds / DAY_SECONDS);
    }

    private static String formatAbsolute(long timestampMs) {
        ZonedDateTime dt = Instant.ofEpochMilli(timestampMs)
                .atZone(ZoneId.systemDefault());
        String base = dt.format(ABSOLUTE_FORMAT);
        String offset = dt.format(OFFSET_FORMAT);
        if (offset == null || offset.isEmpty()) {
            return base;
        }
        return base + " " + offset;
    }
}
