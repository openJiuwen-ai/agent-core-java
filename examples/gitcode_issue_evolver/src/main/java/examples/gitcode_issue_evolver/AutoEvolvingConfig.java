/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * File-backed runtime configuration for the GitCode issue evolver example.
 *
 * @since 0.1.12
 */
@Getter
@Builder(toBuilder = true)
@ToString(exclude = {"webhookSecret", "gitCodeToken", "modelApiKey"})
public final class AutoEvolvingConfig {
    /** Default label that explicitly admits one Issue into the demo. */
    public static final String DEFAULT_TRIGGER_LABEL = "bug";

    private static final URI GITCODE_API_BASE = URI.create("https://api.gitcode.com/api/v5/");
    private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern TEST_SELECTOR_PATTERN = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final int MAX_SMOKE_TEST_SELECTORS = 3;

    @Builder.Default
    private final String bindHost = "127.0.0.1";
    @Builder.Default
    private final int port = 8081;
    private final Path dataDir;
    private final Path worktreeRoot;
    private final Path localRepository;
    private final Path codingStandardSkill;
    private final Path issueWorkerSkill;
    private final String webhookSecret;
    private final String gitCodeToken;
    @Builder.Default
    private final String targetRepository = RepositoryCoordinates.DEFAULT_TARGET_REPOSITORY;
    @Builder.Default
    private final String publishRepository = "";
    @Builder.Default
    private final String baseBranch = "730";
    @Builder.Default
    private final List<String> assignees = List.of();
    @Builder.Default
    private final int workerConcurrency = 1;
    @Builder.Default
    private final TriggerMode triggerMode = TriggerMode.WEBHOOK;
    @Builder.Default
    private final String triggerLabel = DEFAULT_TRIGGER_LABEL;
    @Builder.Default
    private final int issueScanWindowHours = 24;
    @Builder.Default
    private final int pollIntervalMinutes = 15;
    @Builder.Default
    private final int maxIssueScanPages = 10;
    @Builder.Default
    private final boolean manualFullScanEnabled = false;
    @Builder.Default
    private final boolean codeCheckFeedbackEnabled = false;
    @Builder.Default
    private final String codeCheckBotLogin = "openJiuwen-bot";
    @Builder.Default
    private final String codeCheckSuccessLabel = "ci-successful";
    @Builder.Default
    private final String openLibingBaseUrl = "";
    @Builder.Default
    private final int openLibingTimeoutSeconds = 60;
    @Builder.Default
    private final int openLibingMaxFindings = 100;
    @Builder.Default
    private final int maxPrimaryRepairRounds = 5;
    @Builder.Default
    private final int maxDiagnosticRepairRounds = 3;
    @Builder.Default
    private final int maxTransientStageRetries = 5;
    @Builder.Default
    private final boolean smokeTestEnabled = false;
    private final Path smokeTestRepository;
    @Builder.Default
    private final List<String> smokeTestSelectors = List.of();
    @Builder.Default
    private final int smokeTestTimeoutMinutes = 30;
    @Builder.Default
    private final String gitUserName = "gitcode-issue-evolver";
    @Builder.Default
    private final String gitUserEmail = "gitcode-issue-evolver@localhost";
    @Builder.Default
    private final String modelProvider = "";
    @Builder.Default
    private final String modelName = "";
    @Builder.Default
    private final String modelApiBase = "";
    @Builder.Default
    private final String modelApiKey = "";
    @Builder.Default
    private final boolean modelVerifySsl = true;

    /**
     * Return readiness failures without exposing configuration values.
     *
     * @return immutable readiness failure list
     */
    public List<String> readinessErrors() {
        List<String> errors = new ArrayList<>();
        validateNetwork(errors);
        validateCredentials(errors);
        validateRepository(errors);
        validatePaths(errors);
        validateModel(errors);
        return List.copyOf(errors);
    }

    /**
     * Return the fixed official GitCode API base.
     *
     * @return official GitCode API URI
     */
    public URI apiBaseUrl() {
        return GITCODE_API_BASE;
    }

    /**
     * Resolve the shared repository coordinates used by every component.
     *
     * @return validated coordinates
     */
    public RepositoryCoordinates repositoryCoordinates() {
        return RepositoryCoordinates.from(targetRepository, publishRepository, baseBranch);
    }

    /**
     * Resolve the SQLite database below the external runtime directory.
     *
     * @return normalized database path
     */
    public Path databasePath() {
        return Objects.requireNonNull(dataDir, "dataDir must not be null")
                .resolve("auto-evolving.db")
                .normalize();
    }

    /**
     * Resolve the single trusted Skill root staged outside mutable Worktrees.
     *
     * @return trusted Skill staging directory
     */
    public Path trustedSkillsDir() {
        return Objects.requireNonNull(dataDir, "dataDir must not be null")
                .resolve("trusted-skills")
                .normalize();
    }

    private void validateNetwork(List<String> errors) {
        if (bindHost == null || bindHost.isBlank()) {
            errors.add("bindHost is required");
        }
        if (port <= 0 || port > 65535) {
            errors.add("port must be between 1 and 65535");
        }
        if (workerConcurrency != 1) {
            errors.add("workerConcurrency must be 1 for the SQLite demo");
        }
        if (triggerMode == null) {
            errors.add("triggerMode is required");
        }
        if (triggerLabel == null || triggerLabel.isBlank() || triggerLabel.length() > 64) {
            errors.add("triggerLabel must contain between 1 and 64 characters");
        }
        if (issueScanWindowHours < 1 || issueScanWindowHours > 168) {
            errors.add("issueScanWindowHours must be between 1 and 168");
        }
        if (pollIntervalMinutes < 1 || pollIntervalMinutes > 1440) {
            errors.add("pollIntervalMinutes must be between 1 and 1440");
        }
        if (maxIssueScanPages < 1 || maxIssueScanPages > 100) {
            errors.add("maxIssueScanPages must be between 1 and 100");
        }
        if (manualFullScanEnabled && !"127.0.0.1".equals(bindHost)) {
            errors.add("manualFullScanEnabled requires bindHost 127.0.0.1");
        }
        if (manualFullScanEnabled && (triggerMode == null || !triggerMode.usesPolling())) {
            errors.add("manualFullScanEnabled requires polling or both triggerMode");
        }
        if (codeCheckFeedbackEnabled && (triggerMode == null || !triggerMode.usesPolling())) {
            errors.add("codeCheckFeedbackEnabled requires polling or both triggerMode");
        }
        if (codeCheckFeedbackEnabled && invalidAccountName(codeCheckBotLogin)) {
            errors.add("codeCheckBotLogin is invalid");
        }
        if (codeCheckFeedbackEnabled
                && (codeCheckSuccessLabel == null || codeCheckSuccessLabel.isBlank()
                || codeCheckSuccessLabel.length() > 64)) {
            errors.add("codeCheckSuccessLabel must contain between 1 and 64 characters");
        }
        if (codeCheckFeedbackEnabled && !isValidOpenLibingBase(openLibingBaseUrl)) {
            errors.add("openLibingBaseUrl must be a plain HTTPS origin");
        }
        if (openLibingTimeoutSeconds < 5 || openLibingTimeoutSeconds > 300) {
            errors.add("openLibingTimeoutSeconds must be between 5 and 300");
        }
        if (openLibingMaxFindings < 1 || openLibingMaxFindings > 200) {
            errors.add("openLibingMaxFindings must be between 1 and 200");
        }
        if (maxPrimaryRepairRounds < 1 || maxPrimaryRepairRounds > 20) {
            errors.add("maxPrimaryRepairRounds must be between 1 and 20");
        }
        if (maxDiagnosticRepairRounds < 0 || maxDiagnosticRepairRounds > 10) {
            errors.add("maxDiagnosticRepairRounds must be between 0 and 10");
        }
        if (maxTransientStageRetries < 1 || maxTransientStageRetries > 10) {
            errors.add("maxTransientStageRetries must be between 1 and 10");
        }
        if (smokeTestTimeoutMinutes < 1 || smokeTestTimeoutMinutes > 120) {
            errors.add("smokeTestTimeoutMinutes must be between 1 and 120");
        }
    }

    private void validateCredentials(List<String> errors) {
        if (triggerMode != null && triggerMode.usesWebhook()) {
            if (isUnsetValue(webhookSecret)) {
                errors.add("webhookSecret is required when triggerMode enables webhook");
            } else if (webhookSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                errors.add("webhookSecret must contain at least 32 UTF-8 bytes");
            }
        }
        if (isUnsetValue(gitCodeToken)) {
            errors.add("gitCodeToken is required in the local secrets file");
        }
    }

    private void validateRepository(List<String> errors) {
        if (!RepositoryCoordinates.isValidRepository(targetRepository)) {
            errors.add("targetRepository must use owner/name format");
        }
        if (!RepositoryCoordinates.isValidRepository(publishRepository)) {
            errors.add("publishRepository must use owner/name format");
        }
        if (!RepositoryCoordinates.isValidBaseBranch(baseBranch)) {
            errors.add("baseBranch is invalid");
        }
        if (assignees == null || assignees.isEmpty()) {
            errors.add("assignees requires at least one GitCode username");
        } else if (assignees.stream().anyMatch(this::invalidAccountName)) {
            errors.add("assignees contains an invalid GitCode username");
        }
        if (gitUserName == null || gitUserName.isBlank()) {
            errors.add("gitUserName is required");
        }
        if (gitUserEmail == null || gitUserEmail.isBlank()) {
            errors.add("gitUserEmail is required");
        }
    }

    private void validatePaths(List<String> errors) {
        if (!isGitRepository(localRepository)) {
            errors.add("localRepository must point to a readable Git repository");
        }
        if (!isReadableSkill(codingStandardSkill)) {
            errors.add("codingStandardSkill must point to a readable Skill directory");
        }
        if (!isReadableSkill(issueWorkerSkill)) {
            errors.add("issueWorkerSkill must point to a readable Skill directory");
        }
        validateSmokeRepository(errors);
        if (dataDir == null || !ensureWritableDirectory(dataDir)) {
            errors.add("dataDir must be creatable and writable");
        }
        if (worktreeRoot == null || !worktreeRoot.isAbsolute()) {
            errors.add("worktreeRoot must be an absolute path");
            return;
        }
        if (overlaps(worktreeRoot, localRepository)) {
            errors.add("worktreeRoot must be outside localRepository");
        }
        if (overlaps(worktreeRoot, codingStandardSkill) || overlaps(worktreeRoot, issueWorkerSkill)) {
            errors.add("worktreeRoot must be outside trusted Skill directories");
        }
        if (!ensureWritableDirectory(worktreeRoot)) {
            errors.add("worktreeRoot must be creatable and writable");
        }
    }

    private void validateSmokeRepository(List<String> errors) {
        if (!smokeTestEnabled) {
            return;
        }
        if (!isSmokeTestRepository(smokeTestRepository)) {
            errors.add("smokeTestRepository must point to a readable JiuwenTestJava Git repository");
        }
        if (smokeTestSelectors == null || smokeTestSelectors.isEmpty()
                || smokeTestSelectors.size() > MAX_SMOKE_TEST_SELECTORS
                || smokeTestSelectors.stream().anyMatch(this::invalidTestSelector)) {
            errors.add("smokeTestSelectors must contain between 1 and 3 exact Java test class names");
        }
        if (overlaps(smokeTestRepository, localRepository)
                || overlaps(smokeTestRepository, worktreeRoot)
                || overlaps(smokeTestRepository, codingStandardSkill)
                || overlaps(smokeTestRepository, issueWorkerSkill)) {
            errors.add("smokeTestRepository must be isolated from source, Worktree, and Skill paths");
        }
    }

    private void validateModel(List<String> errors) {
        if (isUnsetValue(modelProvider) || isUnsetValue(modelName)
                || isUnsetValue(modelApiBase) || isUnsetValue(modelApiKey)) {
            errors.add("examples/apiconfig.json must provide model provider, name, API base, and API key");
        }
    }

    private static boolean isUnsetValue(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String stripped = value.strip();
        return stripped.startsWith("<") && stripped.endsWith(">");
    }

    private boolean invalidAccountName(String account) {
        return account == null || account.isBlank() || !ACCOUNT_NAME_PATTERN.matcher(account).matches();
    }

    private boolean invalidTestSelector(String selector) {
        return selector == null || !TEST_SELECTOR_PATTERN.matcher(selector).matches();
    }

    private static boolean isValidOpenLibingBase(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && (uri.getPath().isBlank() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isGitRepository(Path directory) {
        return directory != null && Files.isDirectory(directory)
                && Files.isReadable(directory) && Files.exists(directory.resolve(".git"));
    }

    private static boolean isReadableSkill(Path directory) {
        return directory != null && Files.isDirectory(directory)
                && Files.isReadable(directory) && Files.isReadable(directory.resolve("SKILL.md"));
    }

    private static boolean isSmokeTestRepository(Path directory) {
        return isGitRepository(directory)
                && Files.isReadable(directory.resolve("pom.xml"))
                && Files.isDirectory(directory.resolve("src/test/java"));
    }

    private static boolean overlaps(Path first, Path second) {
        if (first == null || second == null) {
            return false;
        }
        Path normalizedFirst = comparablePath(first);
        Path normalizedSecond = comparablePath(second);
        return normalizedFirst.startsWith(normalizedSecond) || normalizedSecond.startsWith(normalizedFirst);
    }

    private static Path comparablePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path ancestor = normalized;
        try {
            while (ancestor != null && !Files.exists(ancestor)) {
                ancestor = ancestor.getParent();
            }
            if (ancestor == null) {
                return normalized;
            }
            return ancestor.toRealPath().resolve(ancestor.relativize(normalized)).normalize();
        } catch (IOException | SecurityException ex) {
            return normalized;
        }
    }

    private static boolean ensureWritableDirectory(Path directory) {
        if (directory == null) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            Path probe = Files.createTempFile(directory, ".evolver-readiness-", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | SecurityException ex) {
            return false;
        }
    }
}
