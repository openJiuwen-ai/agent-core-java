/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import examples.utils.SharedExampleApiConfigLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Loads non-secret runtime settings, local secrets, and the shared example model configuration.
 *
 * @since 0.1.12
 */
public final class ExampleConfigLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExampleConfigLoader() {
    }

    /**
     * Load all demo configuration from explicit files.
     *
     * @param configFile non-secret runtime JSON
     * @param secretsFile local-only GitCode and webhook secrets JSON
     * @param modelFile shared examples/apiconfig.json model configuration
     * @return resolved immutable service configuration
     */
    public static AutoEvolvingConfig load(Path configFile, Path secretsFile, Path modelFile) {
        RuntimeSettings settings = read(requiredFile(configFile, "runtime config"), RuntimeSettings.class);
        SecretSettings secrets = read(requiredFile(secretsFile, "local secrets"), SecretSettings.class);
        Path requiredModelFile = requiredFile(modelFile, "model config");
        System.setProperty("openjiuwen.example.config", requiredModelFile.toString());
        return AutoEvolvingConfig.builder()
                .bindHost(value(settings.bindHost(), "127.0.0.1"))
                .port(settings.port() == null ? 8081 : settings.port())
                .dataDir(path(settings.dataDir(), "../gitcode-issue-evolver-data"))
                .worktreeRoot(path(settings.worktreeRoot(), "../gitcode-issue-evolver-worktrees"))
                .localRepository(path(settings.localRepository(), "."))
                .codingStandardSkill(path(settings.codingStandardSkill(),
                        ".claude/skills/coding-standard-full"))
                .issueWorkerSkill(path(settings.issueWorkerSkill(),
                        "examples/gitcode_issue_evolver/skills/gitcode-issue-evolver-worker"))
                .webhookSecret(value(secrets.webhookSecret(), ""))
                .gitCodeToken(value(secrets.gitCodeToken(), ""))
                .targetRepository(value(settings.targetRepository(),
                        RepositoryCoordinates.DEFAULT_TARGET_REPOSITORY))
                .publishRepository(value(settings.publishRepository(), ""))
                .baseBranch(value(settings.baseBranch(), "730"))
                .assignees(settings.assignees() == null ? List.of() : settings.assignees())
                .workerConcurrency(settings.workerConcurrency() == null ? 1 : settings.workerConcurrency())
                .triggerMode(TriggerMode.parse(settings.triggerMode()))
                .triggerLabel(value(settings.triggerLabel(), AutoEvolvingConfig.DEFAULT_TRIGGER_LABEL))
                .issueScanWindowHours(settings.issueScanWindowHours() == null
                        ? 24 : settings.issueScanWindowHours())
                .pollIntervalMinutes(settings.pollIntervalMinutes() == null
                        ? 15 : settings.pollIntervalMinutes())
                .maxIssueScanPages(settings.maxIssueScanPages() == null
                        ? 10 : settings.maxIssueScanPages())
                .manualFullScanEnabled(Boolean.TRUE.equals(settings.manualFullScanEnabled()))
                .codeCheckFeedbackEnabled(Boolean.TRUE.equals(settings.codeCheckFeedbackEnabled()))
                .codeCheckBotLogin(value(settings.codeCheckBotLogin(), "openJiuwen-bot"))
                .codeCheckSuccessLabel(value(settings.codeCheckSuccessLabel(), "ci-successful"))
                .openLibingBaseUrl(value(settings.openLibingBaseUrl(), ""))
                .openLibingTimeoutSeconds(settings.openLibingTimeoutSeconds() == null
                        ? 60 : settings.openLibingTimeoutSeconds())
                .openLibingMaxFindings(settings.openLibingMaxFindings() == null
                        ? 100 : settings.openLibingMaxFindings())
                .maxPrimaryRepairRounds(settings.maxPrimaryRepairRounds() == null
                        ? 5 : settings.maxPrimaryRepairRounds())
                .maxDiagnosticRepairRounds(settings.maxDiagnosticRepairRounds() == null
                        ? 3 : settings.maxDiagnosticRepairRounds())
                .maxTransientStageRetries(settings.maxTransientStageRetries() == null
                        ? 5 : settings.maxTransientStageRetries())
                .smokeTestEnabled(Boolean.TRUE.equals(settings.smokeTestEnabled()))
                .smokeTestRepository(path(settings.smokeTestRepository(), "../jiuwen-test-java"))
                .smokeTestSelectors(settings.smokeTestSelectors() == null
                        ? List.of() : settings.smokeTestSelectors())
                .smokeTestTimeoutMinutes(settings.smokeTestTimeoutMinutes() == null
                        ? 30 : settings.smokeTestTimeoutMinutes())
                .gitUserName(value(settings.gitUserName(), "gitcode-issue-evolver"))
                .gitUserEmail(value(settings.gitUserEmail(), "gitcode-issue-evolver@localhost"))
                .modelProvider(SharedExampleApiConfigLoader.getModelProvider())
                .modelName(SharedExampleApiConfigLoader.getModelName())
                .modelApiBase(SharedExampleApiConfigLoader.getApiBase())
                .modelApiKey(SharedExampleApiConfigLoader.getApiKey())
                .modelVerifySsl(SharedExampleApiConfigLoader.getSslVerify())
                .build();
    }

    private static Path requiredFile(Path file, String name) {
        Path normalized = Objects.requireNonNull(file, name + " path must not be null")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalStateException(name + " file is unavailable: " + normalized);
        }
        return normalized;
    }

    private static <T> T read(Path file, Class<T> type) {
        try {
            return MAPPER.readValue(file.toFile(), type);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read " + type.getSimpleName() + " JSON", ex);
        }
    }

    private static Path path(String configured, String fallback) {
        String selected = value(configured, fallback);
        try {
            return Path.of(selected).toAbsolutePath().normalize();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Configured path is invalid", ex);
        }
    }

    private static String value(String configured, String fallback) {
        return configured == null || configured.isBlank() ? fallback : configured.strip();
    }

    private record RuntimeSettings(
            String bindHost,
            Integer port,
            String dataDir,
            String worktreeRoot,
            String localRepository,
            String codingStandardSkill,
            String issueWorkerSkill,
            String targetRepository,
            String publishRepository,
            String baseBranch,
            List<String> assignees,
            Integer workerConcurrency,
            String triggerMode,
            String triggerLabel,
            Integer issueScanWindowHours,
            Integer pollIntervalMinutes,
            Integer maxIssueScanPages,
            Boolean manualFullScanEnabled,
            Boolean codeCheckFeedbackEnabled,
            String codeCheckBotLogin,
            String codeCheckSuccessLabel,
            String openLibingBaseUrl,
            Integer openLibingTimeoutSeconds,
            Integer openLibingMaxFindings,
            Integer maxPrimaryRepairRounds,
            Integer maxDiagnosticRepairRounds,
            Integer maxTransientStageRetries,
            Boolean smokeTestEnabled,
            String smokeTestRepository,
            List<String> smokeTestSelectors,
            Integer smokeTestTimeoutMinutes,
            String gitUserName,
            String gitUserEmail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SecretSettings(String gitCodeToken, String webhookSecret) {
    }
}
