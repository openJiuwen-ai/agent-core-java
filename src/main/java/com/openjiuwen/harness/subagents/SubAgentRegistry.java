/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SubAgentRegistry.
 * 
 * @since 0.1.7
 */
public final class SubAgentRegistry {
    private static final Map<String, java.util.function.Function<String, SubAgentConfig>> BUILDERS =
        new LinkedHashMap<>();

    static {
        register("code_agent", CodeAgentFactory::buildCodeAgentConfig);
        register("explore_agent", ExploreAgentFactory::buildExploreAgentConfig);
        register("plan_agent", PlanAgentFactory::buildPlanAgentConfig);
        register("research_agent", ResearchAgentFactory::buildResearchAgentConfig);
        register("verification_agent", VerificationAgentFactory::buildVerificationAgentConfig);
        register("browser_agent", language -> BrowserAgentFactory.buildBrowserAgentConfig(
                com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings.buildRuntimeSettings(), language));
    }

    /**
     * SubAgentRegistry.
     * 
     * @since 0.1.7
     */
    private SubAgentRegistry() {
    }

    /**
     * register.
     * 
     * @param name name
     * @param builder builder
     * @since 0.1.7
     */
    public static void register(String name, java.util.function.Function<String, SubAgentConfig> builder) {
        if (name == null || name.isBlank() || builder == null) {
            throw new IllegalArgumentException("name and builder are required");
        }
        BUILDERS.put(name, builder);
    }

    /**
     * build.
     * 
     * @param name name
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig build(String name, String language) {
        java.util.function.Function<String, SubAgentConfig> builder = BUILDERS.get(normalize(name));
        if (builder == null) {
            throw new IllegalArgumentException("Unknown subagent: " + name);
        }
        return builder.apply(language);
    }

    /**
     * contains.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public static boolean contains(String name) {
        return BUILDERS.containsKey(normalize(name));
    }

    /**
     * names.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static List<String> names() {
        return List.copyOf(BUILDERS.keySet());
    }

    /**
     * normalize.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
