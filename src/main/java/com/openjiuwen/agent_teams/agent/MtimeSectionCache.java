/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

/**
 * mtime-based PromptSection cache.
 * <p>
 * Generic refresh primitive used by TeamRail (and any future rail)
 * to avoid re-fetching slow data on every model call. The cache is
 * unaware of teams or databases -- callers inject:
 * <ul>
 *   <li>probe: a supplier returning a monotonic integer that
 *       increases whenever the underlying data changes</li>
 *   <li>fetchAndBuild: a supplier that performs the full data
 *       fetch and returns the rebuilt PromptSection (or null
 *       when the section should be omitted)</li>
 * </ul>
 * <p>
 * The cache only re-runs fetchAndBuild when probe returns a
 * value different from the last cached probe, so the steady-state cost
 * per call is one cheap probe + one dict lookup.
 * <p>
 * Mirrors Python's {@code MtimeSectionCache} in
 * {@code openjiuwen.agent_teams.agent.team_section_cache}.
 */
public class MtimeSectionCache {

    private final Supplier<CompletableFuture<Integer>> probe;
    private final Supplier<CompletableFuture<PromptSection>> fetchAndBuild;
    private PromptSection cachedSection;
    private int cachedMtime;
    private boolean initialized;

    /**
     * Initialize the cache.
     *
     * @param probe        Supplier returning a monotonic integer that
     *                     increases whenever the underlying data changes.
     * @param fetchAndBuild Supplier that performs the full data fetch
     *                     and returns the rebuilt PromptSection or null.
     */
    public MtimeSectionCache(
            Supplier<CompletableFuture<Integer>> probe,
            Supplier<CompletableFuture<PromptSection>> fetchAndBuild
    ) {
        this.probe = probe;
        this.fetchAndBuild = fetchAndBuild;
        this.cachedSection = null;
        this.cachedMtime = 0;
        this.initialized = false;
    }

    /**
     * Return the current section, refetching only if mtime changed.
     *
     * @return The cached PromptSection (possibly null when the
     *         backing data is empty), reflecting the latest probe.
     */
    public CompletableFuture<PromptSection> refresh() {
        return probe.get().thenCompose(mtime -> {
            if (initialized && mtime == cachedMtime) {
                return CompletableFuture.completedFuture(cachedSection);
            }
            return fetchAndBuild.get().thenApply(section -> {
                cachedSection = section;
                cachedMtime = mtime;
                initialized = true;
                return cachedSection;
            });
        });
    }

    /**
     * Force the next refresh to refetch regardless of mtime.
     */
    public void invalidate() {
        cachedSection = null;
        cachedMtime = 0;
        initialized = false;
    }

    // -- Getters for testing --------------------------------------------------

    public PromptSection getCachedSection() {
        return cachedSection;
    }

    public int getCachedMtime() {
        return cachedMtime;
    }

    public boolean isInitialized() {
        return initialized;
    }
}