/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.infrastructure.RootlessContainerGateRunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Starts the independent file-configured GitCode Feature Evolver service.
 *
 * @since 0.1.12
 */
public final class GitCodeFeatureEvolverExample {
    private static final Path DEFAULT_CONFIG = Path.of(
            "examples", "gitcode_feature_evolver", "config", "feature-config.local.json");
    private static final Path DEFAULT_SECRETS = Path.of(
            "examples", "gitcode_feature_evolver", "config", "feature-secrets.local.json");
    private static final Path DEFAULT_MODEL = Path.of("examples", "apiconfig.json");

    private GitCodeFeatureEvolverExample() {
    }

    /**
     * Load files, check readiness, or run the feature service.
     *
     * @param args optional config, secret, model, check, or help arguments
     * @throws Exception when validation or startup fails
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        if (arguments.action() == Action.HELP) {
            usage();
            return;
        }
        FeatureEvolvingConfig config = FeatureConfigLoader.load(
                arguments.config(), arguments.secrets(), arguments.model());
        switch (arguments.action()) {
            case CHECK -> check(config);
            case CONTAINER_TEST -> containerTest(config, arguments.testWorktree());
            case RUN -> FeatureServiceLauncher.run(config);
            case HELP -> throw new IllegalStateException("help must return before configuration loading");
            default -> throw new IllegalStateException("Unsupported feature service action");
        }
    }

    private static void check(FeatureEvolvingConfig config) {
        RootlessContainerGateRunner container = new RootlessContainerGateRunner(config);
        requireReady(config, container);
        System.out.println("Configuration: READY");
        System.out.println("Target repository: " + config.coordinates().targetRepository());
        System.out.println("Publish repository: " + config.coordinates().publishRepository());
        System.out.println("Base branch: " + config.coordinates().baseBranch());
        System.out.println("System-test delivery: "
                + (config.systemTestEnabled() ? "enabled" : "disabled"));
        if (config.systemTestEnabled()) {
            System.out.println("System-test repository: "
                    + config.systemTestCoordinates().targetRepository());
            System.out.println("System-test publish repository: "
                    + config.systemTestCoordinates().publishRepository());
            System.out.println("System-test base branch: "
                    + config.systemTestCoordinates().baseBranch());
        }
        System.out.println("Trigger mode: " + config.triggerMode().name().toLowerCase(Locale.ROOT));
        System.out.println("Trigger label: " + config.triggerLabel());
        System.out.println("Issue scan field/window: updated_at / "
                + config.issueScanWindowHours() + " hours");
        System.out.println("Poll interval: " + config.pollIntervalMinutes() + " minutes");
        System.out.println("Manual polling endpoint: "
                + (config.manualPollingEnabled() ? "enabled at /admin/poll" : "disabled"));
        System.out.println("Default workflow mode: "
                + config.defaultWorkflowMode().name().toLowerCase(Locale.ROOT));
        System.out.println("Container executor: rootless Podman / network=none / pinned digest");
    }

    private static void containerTest(FeatureEvolvingConfig config, Path worktree) {
        RootlessContainerGateRunner container = new RootlessContainerGateRunner(config);
        requireReady(config, container);
        ContainerGateResult result = container.run(
                RootlessContainerGateRunner.Profile.BASELINE, worktree);
        System.out.println("Container baseline outcome: " + result.outcome());
        System.out.println("Container baseline exit code: " + result.exitCode());
        if (!result.output().isBlank()) {
            System.out.println(result.output());
        }
        if (result.outcome() != ContainerGateResult.Outcome.PASSED) {
            throw new IllegalStateException("Credential-free container baseline did not pass");
        }
    }

    private static void requireReady(FeatureEvolvingConfig config,
                                     RootlessContainerGateRunner container) {
        List<String> errors = FeatureReadiness.errors(config, container);
        if (errors.isEmpty()) {
            return;
        }
        System.out.println("Configuration: NOT READY");
        errors.forEach(error -> System.out.println("- " + error));
        throw new IllegalStateException("GitCode Feature Evolver configuration is not ready");
    }

    private static void usage() {
        System.out.println("GitCodeFeatureEvolverExample options:");
        System.out.println("  --config <path>      Non-secret feature runtime JSON");
        System.out.println("  --secrets <path>     Feature bot and Webhook secrets JSON");
        System.out.println("  --llm-config <path>  Shared examples/apiconfig.json");
        System.out.println("  --check              Validate all mandatory readiness gates");
        System.out.println("  --container-test-worktree <path>");
        System.out.println("                       Run the fixed baseline probe against a trusted Worktree");
        System.out.println("  --help               Show this help");
    }

    private record Arguments(Path config, Path secrets, Path model, Action action,
                             Path testWorktree) {
        private static Arguments parse(String[] args) {
            Path config = DEFAULT_CONFIG;
            Path secrets = DEFAULT_SECRETS;
            Path model = DEFAULT_MODEL;
            Action action = Action.RUN;
            Path testWorktree = null;
            List<String> values = args == null ? List.of() : List.of(args);
            for (int index = 0; index < values.size(); index++) {
                String argument = values.get(index);
                switch (argument) {
                    case "--config" -> config = Path.of(next(values, ++index, argument));
                    case "--secrets" -> secrets = Path.of(next(values, ++index, argument));
                    case "--llm-config" -> model = Path.of(next(values, ++index, argument));
                    case "--check" -> action = select(action, Action.CHECK);
                    case "--container-test-worktree" -> {
                        action = select(action, Action.CONTAINER_TEST);
                        testWorktree = Path.of(next(values, ++index, argument));
                    }
                    case "--help", "-h" -> action = select(action, Action.HELP);
                    default -> throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }
            return new Arguments(config, secrets, model, action, testWorktree);
        }

        private static String next(List<String> values, int index, String option) {
            if (index >= values.size() || values.get(index).startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return values.get(index);
        }

        private static Action select(Action current, Action requested) {
            if (current != Action.RUN) {
                throw new IllegalArgumentException("Only one service action may be selected");
            }
            return requested;
        }
    }

    private enum Action {
        RUN,
        CHECK,
        CONTAINER_TEST,
        HELP
    }
}
