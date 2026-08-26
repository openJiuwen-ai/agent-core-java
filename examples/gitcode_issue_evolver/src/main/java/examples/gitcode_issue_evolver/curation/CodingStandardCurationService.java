/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.curation;

import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs independent, non-blocking coding-standard curation after successful Issue completion.
 *
 * @since 0.1.12
 */
public final class CodingStandardCurationService {
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(CodingStandardCurationService.class);
    private final EvolutionJobStore store;
    private final CodingStandardCuratorAgent agent;

    /**
     * Create the independent curation controller.
     *
     * @param store durable Issue store
     * @param agent isolated Curator Agent
     */
    public CodingStandardCurationService(EvolutionJobStore store,
                                         CodingStandardCuratorAgent agent) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
    }

    /**
     * Process at most one eligible curation task.
     *
     * @return whether a task was found
     */
    public boolean runOnce() {
        Optional<CodingStandardCurationTask> candidate = store.nextCodingStandardCurationTask();
        if (candidate.isEmpty()) {
            return false;
        }
        CodingStandardCurationTask task = candidate.orElseThrow();
        try {
            CodingStandardCurationResult result = agent.curate(task);
            List<CodingStandardLesson> lessons = CodingStandardCurationValidator.validate(task, result);
            store.completeCodingStandardCuration(task, lessons);
            LOGGER.info("Coding-standard curation completed: lessons={}", lessons.size());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            store.failCodingStandardCuration(task, safeMessage(ex), MAX_ATTEMPTS);
            LOGGER.warn("Coding-standard curation attempt failed: attempt={}",
                    task.attemptCount() + 1);
        }
        return true;
    }

    private static String safeMessage(RuntimeException failure) {
        return "Coding-standard curation failed: " + failure.getClass().getSimpleName();
    }
}
