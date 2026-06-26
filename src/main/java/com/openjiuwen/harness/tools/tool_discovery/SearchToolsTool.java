/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.List;
import java.util.Map;

/**
 * Searches progressive tool candidates.
 *
 * <p>Mirrors Python's {@code SearchToolsTool} in
 * {@code openjiuwen/harness/tools/tool_discovery/search_tools.py}.</p>
 */
public class SearchToolsTool extends AbstractHarnessTool {

    private final ToolSearcher toolSearcher;
    private final TraceAppender traceAppender;

    public SearchToolsTool(ToolSearcher toolSearcher, TraceAppender traceAppender) {
        super(toolCard("search_tools", "SearchToolsTool", "Search candidate tools for progressive tool discovery."));
        this.toolSearcher = toolSearcher;
        this.traceAppender = traceAppender;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        try {
            SearchToolsInput parsed = parse(inputs);
            int limit = Math.max(1, Math.min(parsed.limit(), 20));
            List<Map<String, Object>> matches = toolSearcher == null
                    ? List.of()
                    : toolSearcher.search(parsed.query(), limit, parsed.detailLevel());
            Object session = kwargs == null ? null : kwargs.get("session");
            if (traceAppender != null) {
                traceAppender.append(session, Map.of(
                        "action", "search_tools",
                        "query", parsed.query(),
                        "limit", limit,
                        "detail_level", parsed.detailLevel(),
                        "match_count", matches.size()
                ));
            }
            return ToolOutput.success(Map.of(
                    "query", parsed.query(),
                    "matches", matches,
                    "count", matches.size(),
                    "callability_note", "Search results are discovery-only. Tools shown here are not callable until load_tools is called.",
                    "next_step_hint", "If the result is clear enough, call load_tools directly. Increase detail_level to 2 or 3 when you need more parameter detail."
            ));
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    private static SearchToolsInput parse(Map<String, Object> inputs) {
        String query = requiredString(inputs, "query");
        int limit = intValue(inputs == null ? null : inputs.get("limit"), 10);
        int detailLevel = intValue(inputs == null ? null : inputs.get("detail_level"), 1);
        return new SearchToolsInput(query, limit, detailLevel);
    }

    @FunctionalInterface
    public interface ToolSearcher {
        List<Map<String, Object>> search(String query, int limit, int detailLevel) throws Exception;
    }

    @FunctionalInterface
    public interface TraceAppender {
        void append(Object session, Map<String, Object> trace);
    }
}
