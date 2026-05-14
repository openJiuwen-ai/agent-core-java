package com.openjiuwen.auto_harness.pipelines;

import java.util.Map;

/**
 * Mirrors Python's built-in pipeline name constants in {@code openjiuwen.auto_harness.pipelines}.
 */
public final class AutoHarnessPipelineNames {

    public static final String META_EVOLVE_PIPELINE = "meta_evolve_pipeline";
    public static final String EXTENDED_EVOLVE_PIPELINE = "extended_evolve_pipeline";
    private static final Map<String, String> ALIASES = Map.of(
            "pr_pipeline", META_EVOLVE_PIPELINE,
            "extended_harness_pipeline", EXTENDED_EVOLVE_PIPELINE
    );

    private AutoHarnessPipelineNames() {
    }

    public static String normalizePipelineName(String name) {
        return ALIASES.getOrDefault(name, name);
    }
}
