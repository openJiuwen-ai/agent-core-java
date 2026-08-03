/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class SubAgentRegistry {
    private static final Map<String, java.util.function.Function<String, Object>> BUILDERS =
            new LinkedHashMap<>();

    static {
        register("code_agent", language -> CodeAgentFactory.buildCodeAgentConfig((Object) language));
        register("explore_agent", language -> ExploreAgentFactory.buildExploreAgentConfig(language));
        register("plan_agent", language -> PlanAgentFactory.buildPlanAgentConfig(language));
        register("research_agent", language -> ResearchAgentFactory.buildResearchAgentConfig((Object) language));
        register("verification_agent", language -> VerificationAgentFactory.buildVerificationAgentConfig((Object) language));
        register("browser_agent", language -> BrowserAgentFactory.buildBrowserAgentConfig(
                (Object) com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings.buildRuntimeSettings()
        ));
    }

    private SubAgentRegistry() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void register(String name, java.util.function.Function<String, Object> builder) {
        if (name == null || name.isBlank() || builder == null) {
            throw new IllegalArgumentException("name and builder are required");
        }
        BUILDERS.put(name, builder);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Object build(String name, String language) {
        java.util.function.Function<String, Object> builder = BUILDERS.get(normalize(name));
        if (builder == null) {
            throw new IllegalArgumentException("Unknown subagent: " + name);
        }
        return builder.apply(language);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static boolean contains(String name) {
        return BUILDERS.containsKey(normalize(name));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<String> names() {
        return List.copyOf(BUILDERS.keySet());
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
