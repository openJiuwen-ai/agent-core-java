/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.prompts;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * mtime-based {@link PromptSection} cache.
 *
 * <p>Mirrors Python's {@code MtimeSectionCache} in
 * {@code openjiuwen/agent_teams/prompts/section_cache.py}.</p>
 */
public final class MtimeSectionCache {

    private final Supplier<? extends CompletionStage<Long>> probe;
    private final Supplier<? extends CompletionStage<PromptSection>> fetchAndBuild;

    private volatile PromptSection cachedSection;
    private volatile long cachedMtime;
    private volatile boolean initialized;

    public MtimeSectionCache(
            Supplier<? extends CompletionStage<Long>> probe,
            Supplier<? extends CompletionStage<PromptSection>> fetchAndBuild
    ) {
        this.probe = probe;
        this.fetchAndBuild = fetchAndBuild;
    }

    public CompletionStage<PromptSection> refresh() {
        return probe.get().thenCompose(mtime -> {
            if (initialized && mtime == cachedMtime) {
                return CompletableFuture.completedFuture(cachedSection);
            }
            return fetchAndBuild.get().thenApply(section -> {
                cachedSection = section;
                cachedMtime = mtime;
                initialized = true;
                return section;
            });
        });
    }

    public void invalidate() {
        cachedSection = null;
        cachedMtime = 0L;
        initialized = false;
    }
}
