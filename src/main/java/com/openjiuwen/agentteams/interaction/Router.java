/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.interaction;

import com.openjiuwen.agentteams.TeamConstants;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses mention-style routing directives and validates reserved team member names.
 *
 * @since 1.0
 */
public final class Router {
    private static final Pattern MENTION_PATTERN = Pattern.compile("^@(\\S+)\\s+([\\s\\S]+)$");

    private Router() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Optional<MentionRoute> parseMention(String content) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new MentionRoute(matcher.group(1), matcher.group(2)));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean isReservedName(String name) {
        return TeamConstants.RESERVED_MEMBER_NAMES.contains(name);
    }
}
