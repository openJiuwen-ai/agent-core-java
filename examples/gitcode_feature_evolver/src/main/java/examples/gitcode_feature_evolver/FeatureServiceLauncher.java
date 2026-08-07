/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import com.openjiuwen.core.runner.Runner;
import examples.gitcode_feature_evolver.agent.FeatureSkillStager;
import examples.gitcode_feature_evolver.agent.FeatureStageAgent;
import examples.gitcode_feature_evolver.gitcode.FeatureGitCodeClient;
import examples.gitcode_feature_evolver.gitcode.HttpFeatureGitCodeClient;
import examples.gitcode_feature_evolver.infrastructure.FeatureGitPublisher;
import examples.gitcode_feature_evolver.infrastructure.FeatureWorktreeManager;
import examples.gitcode_feature_evolver.infrastructure.RootlessContainerGateRunner;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.job.SqliteFeatureJobStore;
import examples.gitcode_feature_evolver.polling.FeaturePollingCoordinator;
import examples.gitcode_feature_evolver.publish.FeaturePullRequestPublisher;
import examples.gitcode_feature_evolver.worker.FeatureWorker;
import examples.gitcode_feature_evolver.workflow.FeatureStageExecutor;
import examples.gitcode_issue_evolver.AutoEvolvingThreadFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Assembles and runs the independent Feature Evolver process.
 *
 * @since 0.1.12
 */
public final class FeatureServiceLauncher {
    private FeatureServiceLauncher() {
    }

    /**
     * Start the service and block until JVM shutdown.
     *
     * @param config fully resolved feature configuration
     * @throws IOException when the HTTP listener cannot start
     * @throws InterruptedException when process waiting is interrupted
     */
    public static void run(FeatureEvolvingConfig config) throws IOException, InterruptedException {
        FeatureEvolvingConfig required = Objects.requireNonNull(config, "config must not be null");
        RootlessContainerGateRunner container = new RootlessContainerGateRunner(required);
        List<String> readiness = FeatureReadiness.errors(required, container);
        if (!readiness.isEmpty()) {
            throw new IllegalStateException("GitCode Feature Evolver is not ready: "
                    + String.join("; ", readiness));
        }
        Path skillsRoot = FeatureSkillStager.stage(required.trustedSkillsDir(),
                required.featureSkill(), required.codingStandardSkill());
        try (FeatureJobStore store = new SqliteFeatureJobStore(required.databasePath())) {
            runAssembled(required, store, container, skillsRoot, readiness);
        } finally {
            Runner.stop();
        }
    }

    private static void runAssembled(FeatureEvolvingConfig config, FeatureJobStore store,
                                     RootlessContainerGateRunner container, Path skillsRoot,
                                     List<String> readiness) throws IOException, InterruptedException {
        FeatureGitCodeClient gitCode = new HttpFeatureGitCodeClient(
                config.apiBaseUrl(), config.gitCodeToken(), config.coordinates());
        FeatureStageAgent agent = new FeatureStageAgent(config.modelSettings(), skillsRoot);
        FeatureStageExecutor.Infrastructure infrastructure = new FeatureStageExecutor.Infrastructure(
                new FeatureWorktreeManager(config), container, new FeatureGitPublisher(config),
                new FeaturePullRequestPublisher(config, gitCode));
        FeatureStageExecutor executor = new FeatureStageExecutor(config, agent, infrastructure);
        FeatureWorker worker = new FeatureWorker(store, gitCode, executor);
        FeaturePollingCoordinator polling = new FeaturePollingCoordinator(config, store, gitCode);
        FeatureEvolvingService.Components components = new FeatureEvolvingService.Components(
                worker, polling, gitCode);
        try (FeatureEvolvingService service = new FeatureEvolvingService(
                config, store, components, readiness)) {
            awaitService(service);
        }
    }

    private static void awaitService(FeatureEvolvingService service) throws InterruptedException {
        Thread shutdownHook = new AutoEvolvingThreadFactory("feature-evolving-shutdown")
                .newThread(service::close);
        Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(shutdownHook);
        try {
            service.start();
            new CountDownLatch(1).await();
        } finally {
            removeShutdownHook(runtime, shutdownHook);
        }
    }

    private static void removeShutdownHook(Runtime runtime, Thread hook) {
        try {
            runtime.removeShutdownHook(hook);
        } catch (IllegalStateException ex) {
            // The registered hook owns shutdown after JVM termination starts.
        }
    }
}
