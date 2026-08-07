/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.infrastructure;

import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;
import examples.gitcode_issue_evolver.TriggerMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Deterministic fixed-command container and permanent-path-policy checks. */
public final class ContainerAndPathPolicyDeterministicTest {
    private ContainerAndPathPolicyDeterministicTest() {
    }

    /** Run all local container and path checks. */
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("feature-container-");
        FeatureEvolvingConfig config = config(root, TriggerMode.POLLING, "");
        require(config.readinessErrors().isEmpty(),
                "polling-only configuration unexpectedly required a Webhook secret");
        FeatureEvolvingConfig webhook = config(root, TriggerMode.WEBHOOK, "");
        require(webhook.readinessErrors().stream().anyMatch(
                        error -> error.contains("webhookSecret")),
                "Webhook mode did not require its independent HMAC secret");
        Deque<RootlessContainerGateRunner.Execution> results = new ArrayDeque<>();
        List<List<String>> commands = new ArrayList<>();
        RootlessContainerGateRunner runner = new RootlessContainerGateRunner(config,
                (command, directory, timeout) -> {
                    commands.add(List.copyOf(command));
                    return results.removeFirst();
                });

        results.add(new RootlessContainerGateRunner.Execution(0, "true\n", false));
        results.add(new RootlessContainerGateRunner.Execution(0, "", false));
        require(runner.readinessErrors().isEmpty(), "valid rootless runtime was rejected");

        results.add(new RootlessContainerGateRunner.Execution(1,
                "Tests run: 1, Failures: 1, Errors: 0, Skipped: 0", false));
        ContainerGateResult red = runner.run(RootlessContainerGateRunner.Profile.RED, root.resolve("worktree"));
        require(red.expectedRed(), "trustworthy test assertion failure was not accepted as RED");
        List<String> redCommand = commands.get(2);
        require(redCommand.contains("--network=none"), "container networking was not disabled");
        require(redCommand.contains("--read-only=true"), "container root filesystem was not read-only");
        require(redCommand.contains("--http-proxy=false"), "host proxy settings could enter the container");
        require(redCommand.contains("--cap-drop=ALL"), "container capabilities were not dropped");
        require(redCommand.stream().anyMatch(value -> value.contains("dst=/workspace/.git")),
                "the Worktree Git control file was not masked");
        require(redCommand.stream().anyMatch(value -> value.startsWith("--user=1000:1000")),
                "non-root container user was not fixed");
        require(redCommand.contains("-o"), "Maven was not forced into offline mode");
        require(redCommand.stream().anyMatch(value -> value.endsWith(":/m2:ro,Z")),
                "shared Maven cache was not mounted read-only");
        require(redCommand.stream().noneMatch(
                value -> value.toLowerCase(Locale.ROOT).contains("token")),
                "container command exposed a credential-bearing argument");

        results.add(new RootlessContainerGateRunner.Execution(1,
                "Could not resolve dependencies; cannot access central in offline mode", false));
        ContainerGateResult dependency = runner.run(
                RootlessContainerGateRunner.Profile.FULL, root.resolve("worktree"));
        require(dependency.outcome() == ContainerGateResult.Outcome.DEPENDENCY_MISSING,
                "offline dependency miss was not routed to prefetch");

        testPathPolicy();
        System.out.println("ContainerAndPathPolicyDeterministicTest: PASS");
    }

    private static FeatureEvolvingConfig config(Path root, TriggerMode mode,
                                                 String webhookSecret) throws Exception {
        Files.createDirectories(root.resolve("worktree"));
        Files.createDirectories(root.resolve("m2"));
        Files.createDirectories(root.resolve("data"));
        Path repository = root.resolve("repo");
        Path featureSkill = repository.resolve("resources/skills/gitcode-feature-devflow");
        Path codingSkill = repository.resolve("resources/skills/coding-standard");
        Files.createDirectories(repository.resolve(".git"));
        Files.createDirectories(featureSkill);
        Files.createDirectories(codingSkill);
        Files.writeString(featureSkill.resolve("SKILL.md"), "feature");
        Files.writeString(codingSkill.resolve("SKILL.md"), "coding");
        return FeatureEvolvingConfig.builder()
                .dataDir(root.resolve("data"))
                .worktreeRoot(root.resolve("worktree-root"))
                .localRepository(repository)
                .featureSkill(featureSkill)
                .codingStandardSkill(codingSkill)
                .gitCodeToken("deterministic-feature-bot-token")
                .webhookSecret(webhookSecret)
                .triggerMode(mode)
                .approverLogins(List.of("approver"))
                .assignees(List.of("reviewer"))
                .containerRuntime("podman")
                .containerImage("maven:test@sha256:" + "a".repeat(64))
                .containerMavenCache(root.resolve("m2"))
                .containerUser("1000:1000")
                .containerLimits(new FeatureEvolvingConfig.ContainerLimits(1024, "1.5", 128))
                .modelProvider("deterministic")
                .modelName("deterministic")
                .modelApiBase("http://127.0.0.1/model")
                .modelApiKey("deterministic-model-key")
                .build();
    }

    private static void testPathPolicy() {
        List<String> scopes = FeaturePathPolicy.normalizeScopes(List.of(
                "features/77-demo/", "src/main/java/example/Feature.java",
                "src/test/java/example/"));
        require(FeaturePathPolicy.isAllowedWrite("features/77-demo/spec.md", scopes),
                "artifact directory scope was not honored");
        require(FeaturePathPolicy.isAllowedWrite("src/main/java/example/Feature.java", scopes),
                "exact R2-approved file was not honored");
        require(!FeaturePathPolicy.isAllowedWrite("src/main/java/example/Other.java", scopes),
                "unapproved adjacent source file was writable");
        require(!FeaturePathPolicy.isAllowedWrite("resources/skills/coding-standard/SKILL.md", scopes),
                "trusted Skill path bypassed the permanent denylist");
        require(FeaturePathPolicy.isDeniedWrite("pom.xml"),
                "Maven lifecycle configuration was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite(".mvn"),
                "Maven lifecycle directory root was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite("module/.mvn/extensions.xml"),
                "nested Maven lifecycle directory was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite(".github/workflows/release.yml"),
                "CI workflow path was not permanently denied");
        require(FeaturePathPolicy.isDeniedWrite(
                        "module/resources/skills/local/SKILL.md"),
                "nested trusted Skill path was not permanently denied");
        require(FeaturePathPolicy.isSensitiveRead("config/.env"),
                "credential file was not denied for reads");
        require(FeaturePathPolicy.isSensitiveRead("module/.git/config"),
                "nested Git control path was not denied for reads");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
