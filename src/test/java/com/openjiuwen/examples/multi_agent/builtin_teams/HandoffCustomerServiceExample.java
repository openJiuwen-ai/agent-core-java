/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent.builtin_teams;

import com.openjiuwen.core.multiagent.teams.handoff.HandoffRoute;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handoff customer service example.
 *
 * <p>Mirrors Python's {@code handoff_customer_service} in
 * {@code examples.multi_agent.builtin_teams}.</p>
 */
public final class HandoffCustomerServiceExample {

    public static final String TEAM_ID = "customer_service_team";
    public static final String TRIAGE_AGENT = "triage_agent";
    public static final String TECHNICAL_SUPPORT = "technical_support";
    public static final String BILLING_SUPPORT = "billing_support";

    public static final AgentCard TRIAGE_CARD = card(TRIAGE_AGENT,
            "Customer-service triage agent");
    public static final AgentCard TECHNICAL_SUPPORT_CARD = card(TECHNICAL_SUPPORT,
            "Technical support specialist");
    public static final AgentCard BILLING_SUPPORT_CARD = card(BILLING_SUPPORT,
            "Billing support specialist");

    private HandoffCustomerServiceExample() {
        // Utility class
    }

    public static List<HandoffRoute> handoffRoutes() {
        return List.of(
                new HandoffRoute(TRIAGE_AGENT, TECHNICAL_SUPPORT),
                new HandoffRoute(TRIAGE_AGENT, BILLING_SUPPORT),
                new HandoffRoute(TECHNICAL_SUPPORT, BILLING_SUPPORT),
                new HandoffRoute(BILLING_SUPPORT, TECHNICAL_SUPPORT)
        );
    }

    public static String classifyTarget(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "bill", "payment", "refund", "invoice", "charge")) {
            return BILLING_SUPPORT;
        }
        if (containsAny(normalized, "login", "error", "bug", "network", "feature", "technical")) {
            return TECHNICAL_SUPPORT;
        }
        return TRIAGE_AGENT;
    }

    public static Map<String, Object> handleQuery(String query) {
        String target = classifyTarget(query);
        int handoffs = TRIAGE_AGENT.equals(target) ? 0 : 1;
        return Map.of(
                "team_id", TEAM_ID,
                "start_agent", TRIAGE_AGENT,
                "final_agent", target,
                "handoffs", handoffs,
                "query", query == null ? "" : query
        );
    }

    public static List<Map<String, Object>> runSampleCases(List<String> queries) {
        return queries.stream().map(HandoffCustomerServiceExample::handleQuery).toList();
    }

    private static AgentCard card(String id, String description) {
        return AgentCard.builder().id(id).name(id).description(description).build();
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
