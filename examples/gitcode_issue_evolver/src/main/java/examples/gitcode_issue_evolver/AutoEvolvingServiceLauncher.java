/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import com.openjiuwen.core.runner.Runner;
import examples.gitcode_issue_evolver.agent.AgentModelSettings;
import examples.gitcode_issue_evolver.agent.TrustedSkillStager;
import examples.gitcode_issue_evolver.codecheck.HttpOpenLibingCodeCheckClient;
import examples.gitcode_issue_evolver.codecheck.OpenLibingCodeCheckClient;
import examples.gitcode_issue_evolver.curation.CodingStandardCurationService;
import examples.gitcode_issue_evolver.curation.CodingStandardCuratorAgent;
import examples.gitcode_issue_evolver.gitcode.GitCodeClient;
import examples.gitcode_issue_evolver.gitcode.HttpGitCodeClient;
import examples.gitcode_issue_evolver.job.EvolutionJobStore;
import examples.gitcode_issue_evolver.job.SqliteEvolutionJobStore;
import examples.gitcode_issue_evolver.polling.IssuePollingCoordinator;
import examples.gitcode_issue_evolver.profile.AgentCoreJavaRepositoryProfile;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.publish.GitCodeForkPushGateway;
import examples.gitcode_issue_evolver.publish.PullRequestPublisher;
import examples.gitcode_issue_evolver.worker.AutoEvolvingWorker;
import examples.gitcode_issue_evolver.worker.ExampleIssueTaskExecutor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * Launches the GitCode issue evolver demo from one fully resolved file configuration.
 *
 * @since 0.1.12
 */
public final class AutoEvolvingServiceLauncher {
    private AutoEvolvingServiceLauncher() {
    }

    /**
     * Start the service and block until the process is interrupted or shut down.
     *
     * @param config resolved service configuration
     * @throws IOException when the HTTP listener cannot be created
     * @throws InterruptedException when the process wait is interrupted
     */
    public static void run(AutoEvolvingConfig config) throws IOException, InterruptedException {
        AutoEvolvingConfig requiredConfig = Objects.requireNonNull(config, "config must not be null");
        List<String> readinessErrors = requiredConfig.readinessErrors();
        if (!readinessErrors.isEmpty()) {
            throw new IllegalStateException("GitCode Issue Evolver is not ready: "
                    + String.join("; ", readinessErrors));
        }
        RepositoryCoordinates coordinates = requiredConfig.repositoryCoordinates();
        RepositoryProfile profile = new AgentCoreJavaRepositoryProfile(coordinates);
        Path trustedSkillsRoot = TrustedSkillStager.stage(
                requiredConfig.trustedSkillsDir(),
                requiredConfig.getCodingStandardSkill(),
                requiredConfig.getIssueWorkerSkill());
        try (EvolutionJobStore store = new SqliteEvolutionJobStore(requiredConfig.databasePath())) {
            GitCodeClient gitCode = new HttpGitCodeClient(
                    requiredConfig.apiBaseUrl(), requiredConfig.getGitCodeToken(), coordinates);
            PullRequestPublisher publisher = new PullRequestPublisher(
                    gitCode,
                    new GitCodeForkPushGateway(coordinates, coordinates.publishOwner(),
                            requiredConfig.getGitCodeToken()),
                    profile,
                    requiredConfig.getAssignees());
            ExampleIssueTaskExecutor executor = new ExampleIssueTaskExecutor(
                    requiredConfig, profile, publisher, trustedSkillsRoot, store);
            AutoEvolvingWorker worker = new AutoEvolvingWorker(store, gitCode, executor,
                    java.util.UUID.randomUUID().toString(), config.getMaxTransientStageRetries());
            Optional<OpenLibingCodeCheckClient> codeCheckClient = createCodeCheckClient(
                    requiredConfig, coordinates);
            Optional<IssuePollingCoordinator> polling = requiredConfig.getTriggerMode().usesPolling()
                    ? Optional.of(new IssuePollingCoordinator(
                    requiredConfig, store, gitCode, profile, codeCheckClient))
                    : Optional.empty();
            Optional<CodingStandardCurationService> curation = codeCheckClient.map(ignored ->
                    new CodingStandardCurationService(store,
                            new CodingStandardCuratorAgent(modelSettings(requiredConfig),
                                    trustedSkillsRoot)));
            AutoEvolvingService.ServiceComponents components = new AutoEvolvingService.ServiceComponents(
                    Optional.of(worker), polling, curation);
            runService(requiredConfig, store, profile, components);
        } finally {
            Runner.stop();
        }
    }

    private static Optional<OpenLibingCodeCheckClient> createCodeCheckClient(
            AutoEvolvingConfig config, RepositoryCoordinates coordinates) {
        if (!config.isCodeCheckFeedbackEnabled()) {
            return Optional.empty();
        }
        return Optional.of(new HttpOpenLibingCodeCheckClient(
                URI.create(config.getOpenLibingBaseUrl()), coordinates,
                config.getOpenLibingTimeoutSeconds(), config.getOpenLibingMaxFindings()));
    }

    private static AgentModelSettings modelSettings(AutoEvolvingConfig config) {
        return new AgentModelSettings(
                config.getModelProvider(), config.getModelApiKey(), config.getModelApiBase(),
                config.getModelName(), config.isModelVerifySsl());
    }

    private static void runService(AutoEvolvingConfig config, EvolutionJobStore store,
                                   RepositoryProfile profile,
                                   AutoEvolvingService.ServiceComponents components)
            throws IOException, InterruptedException {
        try (AutoEvolvingService service = new AutoEvolvingService(
                config, store, profile, components)) {
            Thread shutdownHook = new AutoEvolvingThreadFactory("auto-evolving-shutdown")
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
    }

    private static void removeShutdownHook(Runtime runtime, Thread shutdownHook) {
        try {
            runtime.removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ex) {
            // The registered hook owns shutdown once JVM termination has started.
        }
    }
}
