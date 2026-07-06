/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.openjiuwen.core.common.logging.Loggers;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-level collector for member results.
 *
 * <p>When a member sends output to the leader via {@code send_message}, the
 * framework captures the content here. Once all members have delivered their
 * results, the framework packages them and delivers a single message to the
 * leader for summarization.
 *
 * <p>This replaces the unreliable "LLM checks its own inbox" pattern with a
 * guaranteed framework-driven delivery.
 */
public final class TeamResultCollector {

    private static final ConcurrentHashMap<String, Map<String, String>> STORE =
            new ConcurrentHashMap<>();

    private TeamResultCollector() {
    }

    /**
     * Record a result from a member. Thread-safe.
     *
     * @param teamName   team identifier
     * @param memberName member name
     * @param content    the full output text
     */
    public static void add(String teamName, String memberName, String content) {
        STORE.computeIfAbsent(teamName, k -> new LinkedHashMap<>()).put(memberName, content);
        Loggers.AGENT.info("TeamResultCollector: recorded result from {} ({} chars) for team {}",
                memberName, content != null ? content.length() : 0, teamName);
    }

    /**
     * Get all collected results for a team. Returns a copy.
     */
    public static Map<String, String> getAll(String teamName) {
        Map<String, String> teamResults = STORE.get(teamName);
        if (teamResults == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(teamResults);
    }

    /**
     * How many results have been collected for this team so far.
     */
    public static int count(String teamName) {
        Map<String, String> teamResults = STORE.get(teamName);
        return teamResults != null ? teamResults.size() : 0;
    }

    /**
     * Remove all results for a team (cleanup after delivery).
     */
    public static void clear(String teamName) {
        STORE.remove(teamName);
        Loggers.AGENT.info("TeamResultCollector: cleared results for team {}", teamName);
    }
}
