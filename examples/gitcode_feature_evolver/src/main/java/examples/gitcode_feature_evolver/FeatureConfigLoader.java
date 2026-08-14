/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.TriggerMode;
import examples.utils.SharedExampleApiConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads feature runtime, secret, and shared model JSON files.
 *
 * @since 0.1.12
 */
public final class FeatureConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureConfigLoader.class);

    private FeatureConfigLoader() {
    }

    /**
     * Load a complete feature service configuration.
     *
     * @param configFile non-secret runtime JSON
     * @param secretsFile local GitCode/Webhook secret JSON
     * @param modelFile shared examples model JSON
     * @return resolved immutable configuration
     */
    public static FeatureEvolvingConfig load(Path configFile, Path secretsFile, Path modelFile) {
        JsonNode settings = read(requiredFile(configFile, "runtime config"));
        JsonNode secrets = read(requiredFile(secretsFile, "local secrets"));
        Path requiredModel = requiredFile(modelFile, "model config");
        System.setProperty("openjiuwen.example.config", requiredModel.toString());
        String target = text(settings, "targetRepository",
                RepositoryCoordinates.DEFAULT_TARGET_REPOSITORY);
        FeatureEvolvingConfig.Builder builder = baseBuilder(settings, secrets, target);
        applyContainer(builder, settings);
        applyModel(builder);
        return builder.build();
    }

    private static FeatureEvolvingConfig.Builder baseBuilder(
            JsonNode settings, JsonNode secrets, String target) {
        String workflowMode = text(settings, "defaultWorkflowMode", "unattended");
        if ("attended".equalsIgnoreCase(workflowMode)) {
            LOGGER.warn("Legacy attended mode is mapped to unattended; only PR merge waits remain");
        }
        return FeatureEvolvingConfig.builder()
                .bindHost(text(settings, "bindHost", "127.0.0.1"))
                .port(integer(settings, "port", 8082))
                .dataDir(path(settings, "dataDir", "../gitcode-feature-evolver-data"))
                .worktreeRoot(path(settings, "worktreeRoot", "../gitcode-feature-evolver-worktrees"))
                .localRepository(path(settings, "localRepository", "."))
                .featureSkill(path(settings, "featureSkill", "resources/skills/gitcode-feature-devflow"))
                .codingStandardSkill(path(settings, "codingStandardSkill", "resources/skills/coding-standard"))
                .webhookSecret(text(secrets, "webhookSecret", ""))
                .gitCodeToken(text(secrets, "gitCodeToken", ""))
                .gitCodeUsername(text(secrets, "gitCodeUsername", ""))
                .systemTestGitCodeToken(text(secrets, "systemTestGitCodeToken", ""))
                .systemTestGitCodeUsername(text(secrets, "systemTestGitCodeUsername", ""))
                .triggerMode(TriggerMode.parse(text(settings, "triggerMode", "both")))
                .triggerLabel(text(settings, "triggerLabel", FeatureEvolvingConfig.DEFAULT_TRIGGER_LABEL))
                .issueScanWindowHours(integer(settings, "issueScanWindowHours", 24))
                .pollIntervalMinutes(integer(settings, "pollIntervalMinutes", 15))
                .manualPollingEnabled(bool(settings, "manualPollingEnabled", false))
                .maxIssueScanPages(integer(settings, "maxIssueScanPages", 10))
                .defaultWorkflowMode(FeatureWorkflowMode.parse(
                        workflowMode))
                .approverLogins(strings(settings.path("approverLogins")))
                .assignees(strings(settings.path("assignees")))
                .componentRoot(text(settings, "componentRoot", "."))
                .targetRepository(target)
                .publishRepository(text(settings, "publishRepository", target))
                .baseBranch(text(settings, "baseBranch", "730"))
                .systemTestEnabled(bool(settings, "systemTestEnabled", false))
                .systemTestRepository(text(settings, "systemTestRepository",
                        "openJiuwen/jiuwen-test"))
                .systemTestPublishRepository(text(settings, "systemTestPublishRepository",
                        "antonjli/jiuwen-test-bot"))
                .systemTestBaseBranch(text(settings, "systemTestBaseBranch", "agent_core_java"))
                .systemTestWriteScopes(systemTestScopes(settings.path("systemTestWriteScopes")))
                .systemTestSmokeSelectors(strings(settings.path("systemTestSmokeSelectors")))
                .gitUserName(text(settings, "gitUserName", "gitcode-feature-evolver"))
                .gitUserEmail(text(settings, "gitUserEmail", "gitcode-feature-evolver@localhost"));
    }

    private static void applyContainer(FeatureEvolvingConfig.Builder builder, JsonNode settings) {
        FeatureEvolvingConfig.ContainerLimits limits = new FeatureEvolvingConfig.ContainerLimits(
                integer(settings, "containerMemoryMb", 2048),
                text(settings, "containerCpus", "2.0"),
                integer(settings, "containerPidsLimit", 256));
        builder.containerRuntime(text(settings, "containerRuntime", "podman"))
                .containerImage(text(settings, "containerImage", ""))
                .containerMavenCache(path(settings, "containerMavenCache",
                        "../gitcode-feature-evolver-m2"))
                .containerUser(text(settings, "containerUser", "1000:1000"))
                .containerTimeoutMinutes(integer(settings, "containerTimeoutMinutes", 30))
                .containerLimits(limits)
                .maxPrimaryRepairRounds(integer(settings, "maxPrimaryRepairRounds", 5))
                .maxDiagnosticRepairRounds(integer(settings, "maxDiagnosticRepairRounds", 3))
                .maxTransientStageRetries(integer(settings, "maxTransientStageRetries", 5))
                .maxDependencyPrefetchRounds(integer(settings, "maxDependencyPrefetchRounds", 2))
                .dependencyPrefetchEnabled(bool(settings, "dependencyPrefetchEnabled", true))
                .dependencyPrefetchCacheRoot(path(settings, "dependencyPrefetchCacheRoot",
                        "../gitcode-feature-evolver-prefetch"))
                .dependencyPrefetchRetentionHours(integer(
                        settings, "dependencyPrefetchRetentionHours", 24));
    }

    private static void applyModel(FeatureEvolvingConfig.Builder builder) {
        builder.modelProvider(SharedExampleApiConfigLoader.getModelProvider())
                .modelName(SharedExampleApiConfigLoader.getModelName())
                .modelApiBase(SharedExampleApiConfigLoader.getApiBase())
                .modelApiKey(SharedExampleApiConfigLoader.getApiKey())
                .modelVerifySsl(SharedExampleApiConfigLoader.getSslVerify());
    }

    private static JsonNode read(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read configuration JSON", ex);
        }
    }

    private static Path requiredFile(Path file, String name) {
        Path normalized = Objects.requireNonNull(file, name + " path must not be null")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalStateException(name + " file is unavailable: " + normalized);
        }
        return normalized;
    }

    private static Path path(JsonNode node, String field, String fallback) {
        try {
            return Path.of(text(node, field, fallback)).toAbsolutePath().normalize();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Configured path is invalid: " + field, ex);
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().strip() : fallback;
    }

    private static int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node.path(field);
        return value.canConvertToInt() ? value.asInt() : fallback;
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.asBoolean() : fallback;
    }

    private static List<String> systemTestScopes(JsonNode node) {
        List<String> configured = strings(node);
        return configured.isEmpty()
                ? List.of("src/test/java/", "src/test/resources/") : configured;
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText().strip());
            }
        });
        return List.copyOf(values);
    }
}
