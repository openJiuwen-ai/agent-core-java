/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.constants.TeamConstants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helper for parsing user-facing mention syntax.
 *
 * <p>Mirrors Python's router helpers in
 * {@code openjiuwen.agent_teams.interaction.router}.
 */
public final class MentionParser {

    private static final Pattern MENTION_PATTERN = Pattern.compile("^@(\\S+)\\s+([\\s\\S]+)$");

    private MentionParser() {
    }

    public static Mention parseMention(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return null;
        }
        return new Mention(matcher.group(1), matcher.group(2));
    }

    public static boolean isReservedName(String name) {
        return name != null && TeamConstants.RESERVED_MEMBER_NAMES.contains(name);
    }

    public record Mention(String target, String body) {
    }
}
