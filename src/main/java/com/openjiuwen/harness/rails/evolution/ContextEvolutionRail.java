/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.CallbackContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context-memory evolution rail.
 *
 * <p>Mirrors Python's {@code ContextEvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/context_evolution_rail.py}.</p>
 */
public class ContextEvolutionRail extends EvolutionRail {

    private final String userId;
    private final boolean injectMemoriesInContext;
    private final boolean autoSummarize;
    private Map<String, Object> currentQuery;

    public ContextEvolutionRail() {
        this("", true, true);
    }

    public ContextEvolutionRail(String userId, boolean injectMemoriesInContext, boolean autoSummarize) {
        this.userId = userId == null ? "" : userId;
        this.injectMemoriesInContext = injectMemoriesInContext;
        this.autoSummarize = autoSummarize;
    }

    @Override
    public void beforeTaskIteration(CallbackContext ctx) {
        currentQuery = new LinkedHashMap<>(ctx.getValues());
        ctx.put("context_evolution_user_id", userId);
        ctx.put("inject_memories_in_context", injectMemoriesInContext);
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        super.afterTaskIteration(ctx);
        ctx.put("auto_summarize", autoSummarize);
    }

    public Map<String, Object> getCurrentQuery() {
        return currentQuery == null ? Map.of() : new LinkedHashMap<>(currentQuery);
    }
}
