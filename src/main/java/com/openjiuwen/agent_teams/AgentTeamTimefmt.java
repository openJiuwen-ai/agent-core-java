/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Human-readable time rendering for agent-facing team text.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.timefmt} in
 * {@code openjiuwen/agent_teams/timefmt.py}.</p>
 */
public final class AgentTeamTimefmt {

    private static final long JUST_NOW_SECONDS = 10L;
    private static final long MINUTE_SECONDS = 60L;
    private static final long HOUR_SECONDS = 60L * 60L;
    private static final long DAY_SECONDS = 24L * 60L * 60L;
    private static final DateTimeFormatter ABSOLUTE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private AgentTeamTimefmt() {
    }

    public static RelativeKeyAndValue relativeKeyAndValue(long deltaMs) {
        if (deltaMs < 0) {
            return new RelativeKeyAndValue("time.just_now", null);
        }
        long seconds = deltaMs / 1000L;
        if (seconds < JUST_NOW_SECONDS) {
            return new RelativeKeyAndValue("time.just_now", null);
        }
        if (seconds < MINUTE_SECONDS) {
            return new RelativeKeyAndValue("time.seconds_ago", seconds);
        }
        if (seconds < HOUR_SECONDS) {
            return new RelativeKeyAndValue("time.minutes_ago", seconds / MINUTE_SECONDS);
        }
        if (seconds < DAY_SECONDS) {
            return new RelativeKeyAndValue("time.hours_ago", seconds / HOUR_SECONDS);
        }
        return new RelativeKeyAndValue("time.days_ago", seconds / DAY_SECONDS);
    }

    public static String formatAbsolute(long timestampMs) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(timestampMs)
                .atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.systemDefault());
        return ABSOLUTE_FORMATTER.format(dateTime);
    }

    public static String formatTimeContext(Long timestampMs, long nowMs) {
        if (timestampMs == null) {
            return AgentTeamI18n.t("time.unknown");
        }
        RelativeKeyAndValue relative = relativeKeyAndValue(nowMs - timestampMs);
        String relativeText = relative.value() == null
                ? AgentTeamI18n.t(relative.key())
                : AgentTeamI18n.t(relative.key(), "value", relative.value());
        return formatAbsolute(timestampMs) + " (" + relativeText + ")";
    }

    /**
     * Bucket-selection result for localized relative-time rendering.
     */
    public record RelativeKeyAndValue(String key, Long value) {
    }
}
