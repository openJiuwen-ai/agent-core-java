/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.sharing.backends.SharingBackend;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Search hub skills by keywords and install packages locally.
 *
 * <p>Mirrors Python's {@code ExperienceHubClient} in
 * {@code openjiuwen/agent_evolving/sharing/hub_client.py}.</p>
 */
public class ExperienceHubClient {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final int DEFAULT_TOP_K = 5;

    private final ExperienceSharer sharer;
    private final EvolutionStore store;

    public ExperienceHubClient(SharingBackend backend, EvolutionStore evolutionStore) {
        this.sharer = new ExperienceSharer(backend, null);
        this.store = evolutionStore;
    }

    public ExperienceSharer getSharer() {
        return sharer;
    }

    public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query) {
        return searchSkills(query, DEFAULT_TOP_K);
    }

    public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
        return sharer.searchSkills(query, topK);
    }

    public CompletionStage<Path> installSkill(String skillId) {
        return installSkill(skillId, null);
    }

    public CompletionStage<Path> installSkill(String skillId, String skillName) {
        String resolvedId = trimToEmpty(skillId);
        if (resolvedId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        byte[] packageBytes = sharer.downloadSkillPackage(resolvedId).toCompletableFuture().join();
        if (packageBytes == null || packageBytes.length == 0) {
            LOGGER.warning("[ExperienceHubClient] no package found for skill_id=%s", resolvedId);
            return CompletableFuture.completedFuture(null);
        }

        SkillPackageMeta meta = sharer.getSkillPackageMeta(resolvedId).toCompletableFuture().join();
        String selectedName = skillName != null && !skillName.isEmpty()
                ? skillName
                : (meta == null ? "" : meta.getSkillName());
        String targetName = trimToNull(selectedName);

        return store.installSkillPackage(packageBytes, targetName).thenApply(installed -> {
            if (installed != null) {
                LOGGER.info(
                        "[ExperienceHubClient] installed skill_id=%s to %s",
                        resolvedId,
                        installed);
            }
            return installed;
        });
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
