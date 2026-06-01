/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Workflow cycle detection.
 * <p>
 * Mirrors Python's {@code CycleChecker} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.cycle_checker}.
 */
public final class CycleChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_FENCE = Pattern.compile("```json\\s*\\n?\\s*(.*?)\\s*\\n?```",
            Pattern.DOTALL);

    private final Object llm;

    public CycleChecker() {
        this(null);
    }

    public CycleChecker(Object llm) {
        this.llm = llm;
    }

    public Object getLlm() {
        return llm;
    }

    /**
     * Parse the LLM JSON response into the Python-compatible tuple payload.
     */
    public static CycleResult parseCycleResultJson(String input) {
        String json = extractJson(input);
        if (json == null || json.isBlank()) {
            return new CycleResult(false, "");
        }
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, new TypeReference<>() {
            });
            Object needRefined = parsed.get("need_refined");
            Object loopDesc = parsed.get("loop_desc");
            return new CycleResult(Boolean.TRUE.equals(needRefined),
                    loopDesc == null ? "" : loopDesc.toString());
        } catch (Exception ignored) {
            return new CycleResult(false, "");
        }
    }

    /**
     * Ask the configured LLM to check Mermaid cycle risks.
     */
    public String checkMermaidCycle(String mermaidCode) {
        if (llm == null) {
            return "{\"need_refined\": false, \"loop_desc\": \"\"}";
        }
        List<Map<String, Object>> messages = List.of(
                Map.of(IntentionDetector.ROLE, "system", IntentionDetector.CONTENT, Prompts.CHECK_CYCLE_SYSTEM_PROMPT),
                Map.of(
                        IntentionDetector.ROLE,
                        "system",
                        IntentionDetector.CONTENT,
                        Prompts.formatCheckCycleUserTemplate(mermaidCode)
                )
        );
        try {
            return IntentionDetector.invokeLlmContent(llm, messages);
        } catch (Exception e) {
            throw new IllegalStateException("Mermaid cycle checking failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check Mermaid code and parse the LLM result.
     */
    public CycleResult checkAndParse(String mermaidCode) {
        return parseCycleResultJson(checkMermaidCycle(mermaidCode));
    }

    /** Check if a graph has cycles. */
    public static boolean hasCycle(Map<String, List<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (dfs(node, graph, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfs(String node, Map<String, List<String>> graph,
                                Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        inStack.add(node);

        List<String> neighbors = graph.getOrDefault(node, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (dfs(neighbor, graph, visited, inStack)) {
                return true;
            }
        }

        inStack.remove(node);
        return false;
    }

    private static String extractJson(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = JSON_FENCE.matcher(input);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return input.trim();
    }

    /**
     * Java representation of Python's {@code Tuple[bool, str]} return.
     */
    public record CycleResult(boolean needRefined, String loopDesc) {
    }
}
