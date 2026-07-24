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
    /** Label that explicitly admits one Issue update into the demo. */
    public static final String TRIGGER_LABEL = "bug";

    private static final URI GITCODE_API_BASE = URI.create("https://api.gitcode.com/api/v5/");
    private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

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
    }

    private void validateCredentials(List<String> errors) {
        if (isUnsetValue(webhookSecret)) {
            errors.add("webhookSecret is required in the local secrets file");
        } else if (webhookSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            errors.add("webhookSecret must contain at least 32 UTF-8 bytes");
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

    private static boolean isGitRepository(Path directory) {
        return directory != null && Files.isDirectory(directory)
                && Files.isReadable(directory) && Files.exists(directory.resolve(".git"));
    }

    private static boolean isReadableSkill(Path directory) {
        return directory != null && Files.isDirectory(directory)
                && Files.isReadable(directory) && Files.isReadable(directory.resolve("SKILL.md"));
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
