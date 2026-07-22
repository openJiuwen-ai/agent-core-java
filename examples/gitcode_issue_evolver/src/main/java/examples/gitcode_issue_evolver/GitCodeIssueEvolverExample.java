/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import java.nio.file.Path;
import java.util.List;

/**
 * Starts the file-configured GitCode Issue Evolver ReAct Agent demo.
 *
 * @since 0.1.12
 */
public final class GitCodeIssueEvolverExample {
    private static final Path DEFAULT_CONFIG = Path.of(
            "examples", "gitcode_issue_evolver", "config", "evolver-config.local.json");
    private static final Path DEFAULT_SECRETS = Path.of(
            "examples", "gitcode_issue_evolver", "config", "evolver-secrets.local.json");
    private static final Path DEFAULT_MODEL_CONFIG = Path.of("examples", "apiconfig.json");

    private GitCodeIssueEvolverExample() {
    }

    /**
     * Load local files and start the webhook service.
     *
     * @param args optional --config, --secrets, --llm-config, --check, or --help arguments
     * @throws Exception when configuration or service startup fails
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        if (arguments.help()) {
            printUsage();
            return;
        }
        AutoEvolvingConfig config = ExampleConfigLoader.load(
                arguments.config(), arguments.secrets(), arguments.modelConfig());
        if (arguments.check()) {
            check(config);
            return;
        }
        AutoEvolvingServiceLauncher.run(config);
    }

    private static void check(AutoEvolvingConfig config) {
        List<String> errors = config.readinessErrors();
        RepositoryCoordinates coordinates = errors.isEmpty()
                ? config.repositoryCoordinates() : null;
        if (coordinates != null) {
            System.out.println("Configuration: READY");
            System.out.println("Target repository: " + coordinates.targetRepository());
            System.out.println("Publish repository: " + coordinates.publishRepository());
            System.out.println("Base branch: " + coordinates.baseBranch());
            System.out.println("Trigger: Issue update adding label " + AutoEvolvingConfig.TRIGGER_LABEL);
            return;
        }
        System.out.println("Configuration: NOT READY");
        errors.forEach(error -> System.out.println("- " + error));
        throw new IllegalStateException("GitCode Issue Evolver configuration is not ready");
    }

    private static void printUsage() {
        System.out.println("GitCodeIssueEvolverExample options:");
        System.out.println("  --config <path>      Non-secret runtime JSON");
        System.out.println("  --secrets <path>     Local GitCode and webhook secrets JSON");
        System.out.println("  --llm-config <path>  Shared examples/apiconfig.json");
        System.out.println("  --check              Validate configuration without starting the service");
        System.out.println("  --help               Show this help");
    }

    private record Arguments(Path config, Path secrets, Path modelConfig,
                             boolean check, boolean help) {
        private static Arguments parse(String[] args) {
            Path config = DEFAULT_CONFIG;
            Path secrets = DEFAULT_SECRETS;
            Path modelConfig = DEFAULT_MODEL_CONFIG;
            boolean check = false;
            boolean help = false;
            List<String> values = args == null ? List.of() : List.of(args);
            for (int index = 0; index < values.size(); index++) {
                String argument = values.get(index);
                switch (argument) {
                    case "--config" -> config = Path.of(next(values, ++index, argument));
                    case "--secrets" -> secrets = Path.of(next(values, ++index, argument));
                    case "--llm-config" -> modelConfig = Path.of(next(values, ++index, argument));
                    case "--check" -> check = true;
                    case "--help", "-h" -> help = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }
            return new Arguments(config, secrets, modelConfig, check, help);
        }

        private static String next(List<String> values, int index, String option) {
            if (index >= values.size() || values.get(index).startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return values.get(index);
        }
    }
}
