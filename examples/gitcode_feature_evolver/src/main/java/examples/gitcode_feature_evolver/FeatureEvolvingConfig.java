/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver;

import examples.gitcode_issue_evolver.RepositoryCoordinates;
import examples.gitcode_issue_evolver.TriggerMode;
import examples.gitcode_issue_evolver.agent.AgentModelSettings;

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
 * Immutable file-backed configuration for the independent feature evolver.
 *
 * @since 0.1.12
 */
public final class FeatureEvolvingConfig {
    /** Exact default Issue label admitted by the feature service. */
    public static final String DEFAULT_TRIGGER_LABEL = "feature";
    private static final URI GITCODE_API_BASE = URI.create("https://api.gitcode.com/api/v5/");
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern IMAGE_DIGEST_PATTERN = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:/-]*@sha256:[0-9a-fA-F]{64}");
    private static final Pattern TEST_SELECTOR_PATTERN = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final int MAX_SYSTEM_TEST_SMOKE_SELECTORS = 3;
    private static final List<String> DEFAULT_SYSTEM_TEST_WRITE_SCOPES = List.of(
            "src/test/java/", "src/test/resources/");
    private final Builder values;

    private FeatureEvolvingConfig(Builder builder) {
        Builder resolved = new Builder(Objects.requireNonNull(builder, "builder must not be null"));
        if (resolved.dependencyPrefetchCacheRoot == null && resolved.dataDir != null) {
            resolved.dependencyPrefetchCacheRoot = resolved.dataDir.resolve("prefetch").normalize();
        }
        this.values = resolved;
    }

    /** @return a mutable builder initialized with secure defaults */
    public static Builder builder() {
        return new Builder();
    }

    /** @return listener host */
    public String bindHost() {
        return values.bindHost;
    }

    /** @return listener port */
    public int port() {
        return values.port;
    }

    /** @return external runtime data directory */
    public Path dataDir() {
        return values.dataDir;
    }

    /** @return persistent feature Worktree root */
    public Path worktreeRoot() {
        return values.worktreeRoot;
    }

    /** @return local seed Git repository */
    public Path localRepository() {
        return values.localRepository;
    }

    /** @return primary Feature DevFlow Skill source */
    public Path featureSkill() {
        return values.featureSkill;
    }

    /** @return coding-standard Skill overlay source */
    public Path codingStandardSkill() {
        return values.codingStandardSkill;
    }

    /** @return GitCode Webhook HMAC secret */
    public String webhookSecret() {
        return values.webhookSecret;
    }

    /** @return Evolver bot GitCode token */
    public String gitCodeToken() {
        return values.gitCodeToken;
    }

    /** @return GitCode login that owns the Evolver bot PAT */
    public String gitCodeUsername() {
        return isUnset(values.gitCodeUsername)
                ? coordinates().publishOwner() : values.gitCodeUsername;
    }

    /** @return GitCode login used for test-repository Git operations */
    public String systemTestGitCodeUsername() {
        if (!isUnset(values.systemTestGitCodeUsername)) {
            return values.systemTestGitCodeUsername;
        }
        return isUnset(values.systemTestGitCodeToken)
                ? gitCodeUsername() : systemTestCoordinates().publishOwner();
    }

    /** @return isolated test-repository PAT, or the Feature Bot PAT when absent */
    public String systemTestGitCodeToken() {
        return isUnset(values.systemTestGitCodeToken)
                ? values.gitCodeToken : values.systemTestGitCodeToken;
    }

    /** @return configured trigger mode */
    public TriggerMode triggerMode() {
        return values.triggerMode;
    }

    /** @return exact feature trigger label */
    public String triggerLabel() {
        return values.triggerLabel;
    }

    /** @return updated-at scan window in hours */
    public int issueScanWindowHours() {
        return values.issueScanWindowHours;
    }

    /** @return fixed polling delay in minutes */
    public int pollIntervalMinutes() {
        return values.pollIntervalMinutes;
    }

    /** @return whether the loopback-only manual polling endpoint is enabled */
    public boolean manualPollingEnabled() {
        return values.manualPollingEnabled;
    }

    /** @return maximum Issue pages per scan iteration */
    public int maxIssueScanPages() {
        return values.maxIssueScanPages;
    }

    /** @return default human participation mode */
    public FeatureWorkflowMode defaultWorkflowMode() {
        return values.defaultWorkflowMode;
    }

    /** @return authenticated command author allowlist */
    public List<String> approverLogins() {
        return List.copyOf(values.approverLogins);
    }

    /** @return pull-request assignees */
    public List<String> assignees() {
        return List.copyOf(values.assignees);
    }

    /** @return repository-relative component root used for DevFlow artifacts */
    public String componentRoot() {
        return values.componentRoot;
    }

    /** @return validated target and publication coordinates */
    public RepositoryCoordinates coordinates() {
        return RepositoryCoordinates.from(values.targetRepository,
                values.publishRepository, values.baseBranch);
    }

    /** @return whether a merged feature must produce a separate system-test PR */
    public boolean systemTestEnabled() {
        return values.systemTestEnabled;
    }

    /** @return target and fork-publication coordinates for post-merge system tests */
    public RepositoryCoordinates systemTestCoordinates() {
        return RepositoryCoordinates.from(values.systemTestRepository,
                values.systemTestPublishRepository, values.systemTestBaseBranch);
    }

    /** @return exact test-code/resource directory scopes writable after feature merge */
    public List<String> systemTestWriteScopes() {
        return List.copyOf(values.systemTestWriteScopes);
    }

    /** @return operator-approved smoke classes always run with new system tests */
    public List<String> systemTestSmokeSelectors() {
        return List.copyOf(values.systemTestSmokeSelectors);
    }

    /** @return fixed official GitCode API base */
    public URI apiBaseUrl() {
        return GITCODE_API_BASE;
    }

    /** @return Git commit author name */
    public String gitUserName() {
        return values.gitUserName;
    }

    /** @return Git commit author email */
    public String gitUserEmail() {
        return values.gitUserEmail;
    }

    /** @return exact rootless container executable name */
    public String containerRuntime() {
        return values.containerRuntime;
    }

    /** @return digest-pinned container image */
    public String containerImage() {
        return values.containerImage;
    }

    /** @return credential-free Maven dependency cache */
    public Path containerMavenCache() {
        return values.containerMavenCache;
    }

    /** @return non-root container UID:GID mapped to the service account */
    public String containerUser() {
        return values.containerUser;
    }

    /** @return bounded container execution timeout in minutes */
    public int containerTimeoutMinutes() {
        return values.containerTimeoutMinutes;
    }

    /** @return immutable container limits */
    public ContainerLimits containerLimits() {
        return values.containerLimits;
    }

    /** @return maximum input-changing repairs in the primary Agent session */
    public int maxPrimaryRepairRounds() {
        return values.maxPrimaryRepairRounds;
    }

    /** @return maximum repairs in the independent diagnostic Agent session */
    public int maxDiagnosticRepairRounds() {
        return values.maxDiagnosticRepairRounds;
    }

    /** @return maximum scheduled retries for one transient stage failure */
    public int maxTransientStageRetries() {
        return values.maxTransientStageRetries;
    }

    /** @return maximum automatic dependency-prefetch rounds */
    public int maxDependencyPrefetchRounds() {
        return values.maxDependencyPrefetchRounds;
    }

    /** @return whether isolated automatic dependency prefetch is enabled */
    public boolean dependencyPrefetchEnabled() {
        return values.dependencyPrefetchEnabled;
    }

    /** @return root for isolated per-Job Maven caches */
    public Path dependencyPrefetchCacheRoot() {
        return values.dependencyPrefetchCacheRoot;
    }

    /** @return terminal Job cache retention in hours */
    public int dependencyPrefetchRetentionHours() {
        return values.dependencyPrefetchRetentionHours;
    }

    /** @return model-only settings without GitCode credentials */
    public AgentModelSettings modelSettings() {
        return new AgentModelSettings(values.modelProvider, values.modelApiKey,
                values.modelApiBase, values.modelName, values.modelVerifySsl);
    }

    /** @return SQLite database below the external data directory */
    public Path databasePath() {
        return values.dataDir.resolve("feature-evolving.db").normalize();
    }

    /** @return immutable-for-Agent Skill staging root */
    public Path trustedSkillsDir() {
        return values.dataDir.resolve("trusted-skills").normalize();
    }

    /**
     * Return non-sensitive configuration failures.
     *
     * @return immutable readiness error list
     */
    public List<String> readinessErrors() {
        List<String> errors = new ArrayList<>();
        validateNetwork(errors);
        validateCredentials(errors);
        validateRepository(errors);
        validateSystemTest(errors);
        validatePaths(errors);
        validateContainer(errors);
        validateModel(errors);
        return List.copyOf(errors);
    }

    private void validateNetwork(List<String> errors) {
        if (values.bindHost.isBlank()) {
            errors.add("bindHost is required");
        } else if (!List.of("127.0.0.1", "::1", "localhost").contains(values.bindHost)) {
            errors.add("bindHost must be a loopback address");
        }
        if (values.port <= 0 || values.port > 65535) {
            errors.add("port must be between 1 and 65535");
        }
        if (values.triggerMode == null) {
            errors.add("triggerMode is required");
        }
        if (values.triggerLabel.isBlank() || values.triggerLabel.length() > 64) {
            errors.add("triggerLabel must contain between 1 and 64 characters");
        }
        if (values.issueScanWindowHours < 1 || values.issueScanWindowHours > 168) {
            errors.add("issueScanWindowHours must be between 1 and 168");
        }
        if (values.pollIntervalMinutes < 1 || values.pollIntervalMinutes > 1440) {
            errors.add("pollIntervalMinutes must be between 1 and 1440");
        }
        if (values.manualPollingEnabled && !"127.0.0.1".equals(values.bindHost)) {
            errors.add("manualPollingEnabled requires bindHost 127.0.0.1");
        }
        if (values.manualPollingEnabled
                && (values.triggerMode == null || !values.triggerMode.usesPolling())) {
            errors.add("manualPollingEnabled requires polling or both triggerMode");
        }
        if (values.maxIssueScanPages < 1 || values.maxIssueScanPages > 100) {
            errors.add("maxIssueScanPages must be between 1 and 100");
        }
    }

    private void validateCredentials(List<String> errors) {
        if (values.triggerMode != null && values.triggerMode.usesWebhook()) {
            if (isUnset(values.webhookSecret)) {
                errors.add("webhookSecret is required when triggerMode enables webhook");
            } else if (values.webhookSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                errors.add("webhookSecret must contain at least 32 UTF-8 bytes");
            }
        }
        if (isUnset(values.gitCodeToken)) {
            errors.add("gitCodeToken is required in the feature service secrets file");
        }
    }

    private void validateRepository(List<String> errors) {
        if (!RepositoryCoordinates.isValidRepository(values.targetRepository)) {
            errors.add("targetRepository must use owner/name format");
        }
        if (!RepositoryCoordinates.isValidRepository(values.publishRepository)) {
            errors.add("publishRepository must use owner/name format");
        }
        if (!RepositoryCoordinates.isValidBaseBranch(values.baseBranch)) {
            errors.add("baseBranch is invalid");
        }
        if (!isSafeComponentRoot(values.componentRoot)) {
            errors.add("componentRoot must be a safe repository-relative directory");
        }
        validateAccounts(values.assignees, "assignees", errors);
        validateAccounts(values.approverLogins, "approverLogins", errors);
        if (values.gitUserName.isBlank() || values.gitUserEmail.isBlank()) {
            errors.add("Git author name and email are required");
        }
    }

    private void validateSystemTest(List<String> errors) {
        if (!values.systemTestEnabled) {
            return;
        }
        if (!RepositoryCoordinates.isValidRepository(values.systemTestRepository)) {
            errors.add("systemTestRepository must use owner/name format");
        }
        if (!RepositoryCoordinates.isValidRepository(values.systemTestPublishRepository)) {
            errors.add("systemTestPublishRepository must use owner/name format");
        }
        if (!RepositoryCoordinates.isValidBaseBranch(values.systemTestBaseBranch)) {
            errors.add("systemTestBaseBranch is invalid");
        }
        if (values.systemTestWriteScopes.isEmpty()
                || values.systemTestWriteScopes.stream().anyMatch(
                scope -> !isSafeSystemTestScope(scope))) {
            errors.add("systemTestWriteScopes must contain only src/test/java/ or src/test/resources/ directories");
        }
        if (values.systemTestSmokeSelectors.isEmpty()
                || values.systemTestSmokeSelectors.size() > MAX_SYSTEM_TEST_SMOKE_SELECTORS
                || values.systemTestSmokeSelectors.stream().anyMatch(
                selector -> !isExactTestSelector(selector))) {
            errors.add("systemTestSmokeSelectors must contain between 1 and 3 exact Java test class names");
        }
    }

    private static boolean isExactTestSelector(String selector) {
        return selector != null && TEST_SELECTOR_PATTERN.matcher(selector).matches();
    }

    private static boolean isSafeSystemTestScope(String scope) {
        if (scope == null || !scope.endsWith("/")) {
            return false;
        }
        String value = scope.replace('\\', '/');
        return "src/test/java/".equals(value) || value.startsWith("src/test/java/")
                || "src/test/resources/".equals(value) || value.startsWith("src/test/resources/");
    }

    private void validateAccounts(List<String> accounts, String name, List<String> errors) {
        if (accounts.isEmpty()) {
            errors.add(name + " requires at least one GitCode username");
            return;
        }
        if (accounts.stream().anyMatch(this::isInvalidAccount)) {
            errors.add(name + " contains an invalid GitCode username");
        }
    }

    private void validatePaths(List<String> errors) {
        if (!isGitRepository(values.localRepository)) {
            errors.add("localRepository must point to a readable Git repository");
        }
        if (!isReadableSkill(values.featureSkill)) {
            errors.add("featureSkill must point to a readable Skill directory");
        }
        if (!isReadableSkill(values.codingStandardSkill)) {
            errors.add("codingStandardSkill must point to a readable Skill directory");
        }
        if (!ensureWritableDirectory(values.dataDir)) {
            errors.add("dataDir must be creatable and writable");
        }
        if (!isExternalWritable(values.worktreeRoot, errors)) {
            return;
        }
        if (overlaps(values.worktreeRoot, values.localRepository)
                || overlaps(values.worktreeRoot, values.featureSkill)
                || overlaps(values.worktreeRoot, values.codingStandardSkill)) {
            errors.add("worktreeRoot must be outside the repository and trusted Skills");
        }
        if (overlaps(values.dataDir, values.localRepository)
                || overlaps(values.dataDir, values.featureSkill)
                || overlaps(values.dataDir, values.codingStandardSkill)
                || overlaps(values.dataDir, values.worktreeRoot)) {
            errors.add("dataDir must be outside the repository, Skills, and worktreeRoot");
        }
    }

    private boolean isExternalWritable(Path directory, List<String> errors) {
        if (directory == null || !directory.isAbsolute()) {
            errors.add("worktreeRoot must be an absolute path");
            return false;
        }
        if (!ensureWritableDirectory(directory)) {
            errors.add("worktreeRoot must be creatable and writable");
            return false;
        }
        return true;
    }

    private void validateContainer(List<String> errors) {
        if (!"podman".equals(values.containerRuntime)) {
            errors.add("containerRuntime must be podman for rootless execution");
        }
        if (!IMAGE_DIGEST_PATTERN.matcher(values.containerImage).matches()) {
            errors.add("containerImage must be pinned as name@sha256:<64 hex characters>");
        }
        if (!values.containerUser.matches("[1-9][0-9]{0,8}:[1-9][0-9]{0,8}")) {
            errors.add("containerUser must use a non-root numeric UID:GID");
        }
        if (!ensureWritableDirectory(values.containerMavenCache)) {
            errors.add("containerMavenCache must be creatable and writable");
        }
        if (overlaps(values.containerMavenCache, values.worktreeRoot)
                || overlaps(values.containerMavenCache, values.localRepository)
                || overlaps(values.containerMavenCache, values.dataDir)) {
            errors.add("containerMavenCache must be outside the repository, dataDir, and worktreeRoot");
        }
        if (values.containerTimeoutMinutes < 1 || values.containerTimeoutMinutes > 120) {
            errors.add("containerTimeoutMinutes must be between 1 and 120");
        }
        if (!values.containerLimits.isValid()) {
            errors.add("container resource limits are invalid");
        }
        if (values.maxPrimaryRepairRounds < 1 || values.maxPrimaryRepairRounds > 20) {
            errors.add("maxPrimaryRepairRounds must be between 1 and 20");
        }
        if (values.maxDiagnosticRepairRounds < 0 || values.maxDiagnosticRepairRounds > 10) {
            errors.add("maxDiagnosticRepairRounds must be between 0 and 10");
        }
        if (values.maxTransientStageRetries < 1 || values.maxTransientStageRetries > 5) {
            errors.add("maxTransientStageRetries must be between 1 and 5");
        }
        if (values.maxDependencyPrefetchRounds < 0 || values.maxDependencyPrefetchRounds > 5) {
            errors.add("maxDependencyPrefetchRounds must be between 0 and 5");
        }
        if (values.dependencyPrefetchRetentionHours < 1
                || values.dependencyPrefetchRetentionHours > 168) {
            errors.add("dependencyPrefetchRetentionHours must be between 1 and 168");
        }
        if (values.dependencyPrefetchEnabled
                && !ensureWritableDirectory(values.dependencyPrefetchCacheRoot)) {
            errors.add("dependencyPrefetchCacheRoot must be creatable and writable");
        }
        if (values.dependencyPrefetchEnabled
                && (overlaps(values.dependencyPrefetchCacheRoot, values.localRepository)
                || overlaps(values.dependencyPrefetchCacheRoot, values.worktreeRoot)
                || overlaps(values.dependencyPrefetchCacheRoot, values.containerMavenCache))) {
            errors.add("dependencyPrefetchCacheRoot must be isolated from repositories and shared cache");
        }
    }

    private void validateModel(List<String> errors) {
        if (isUnset(values.modelProvider) || isUnset(values.modelName)
                || isUnset(values.modelApiBase) || isUnset(values.modelApiKey)) {
            errors.add("model config must provide provider, name, API base, and API key");
        }
    }

    private boolean isInvalidAccount(String account) {
        return account == null || !ACCOUNT_PATTERN.matcher(account).matches();
    }

    private static boolean isUnset(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String stripped = value.strip();
        return stripped.startsWith("<") && stripped.endsWith(">");
    }

    private static boolean isGitRepository(Path directory) {
        return directory != null && Files.isDirectory(directory) && Files.isReadable(directory)
                && Files.exists(directory.resolve(".git"));
    }

    private static boolean isSafeComponentRoot(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\")) {
            return false;
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (path.isAbsolute()) {
            return false;
        }
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReadableSkill(Path directory) {
        return directory != null && Files.isDirectory(directory) && Files.isReadable(directory)
                && Files.isReadable(directory.resolve("SKILL.md"));
    }

    private static boolean overlaps(Path first, Path second) {
        if (first == null || second == null) {
            return false;
        }
        Path left = first.toAbsolutePath().normalize();
        Path right = second.toAbsolutePath().normalize();
        return left.startsWith(right) || right.startsWith(left);
    }

    private static boolean ensureWritableDirectory(Path directory) {
        if (directory == null) {
            return false;
        }
        try {
            Files.createDirectories(directory);
            Path probe = Files.createTempFile(directory, ".feature-evolver-readiness-", ".tmp");
            Files.delete(probe);
            return true;
        } catch (IOException | SecurityException ex) {
            return false;
        }
    }

    /**
     * Rootless container limits.
     *
     * @param memoryMb maximum memory in MiB
     * @param cpus maximum CPU quota
     * @param pidsLimit maximum process count
     * @since 0.1.12
     */
    public record ContainerLimits(int memoryMb, String cpus, int pidsLimit) {
        /** Normalize the CPU limit string. */
        public ContainerLimits {
            cpus = cpus == null ? "" : cpus.strip();
        }

        private boolean isValid() {
            if (memoryMb < 256 || memoryMb > 32768 || pidsLimit < 32 || pidsLimit > 4096) {
                return false;
            }
            try {
                double cpuValue = Double.parseDouble(cpus);
                return Double.isFinite(cpuValue) && cpuValue >= 0.25 && cpuValue <= 32.0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    /**
     * Mutable builder with non-secret production defaults.
     *
     * @since 0.1.12
     */
    public static final class Builder {
        private String bindHost = "127.0.0.1";
        private int port = 8082;
        private Path dataDir;
        private Path worktreeRoot;
        private Path localRepository;
        private Path featureSkill;
        private Path codingStandardSkill;
        private String webhookSecret = "";
        private String gitCodeToken = "";
        private String gitCodeUsername = "";
        private String systemTestGitCodeToken = "";
        private String systemTestGitCodeUsername = "";
        private TriggerMode triggerMode = TriggerMode.BOTH;
        private String triggerLabel = DEFAULT_TRIGGER_LABEL;
        private int issueScanWindowHours = 24;
        private int pollIntervalMinutes = 15;
        private boolean manualPollingEnabled;
        private int maxIssueScanPages = 10;
        private FeatureWorkflowMode defaultWorkflowMode = FeatureWorkflowMode.UNATTENDED;
        private List<String> approverLogins = List.of();
        private List<String> assignees = List.of();
        private String componentRoot = ".";
        private String targetRepository = RepositoryCoordinates.DEFAULT_TARGET_REPOSITORY;
        private String publishRepository = RepositoryCoordinates.DEFAULT_TARGET_REPOSITORY;
        private String baseBranch = "730";
        private boolean systemTestEnabled;
        private String systemTestRepository = "openJiuwen/jiuwen-test";
        private String systemTestPublishRepository = "antonjli/jiuwen-test-bot";
        private String systemTestBaseBranch = "agent_core_java";
        private List<String> systemTestWriteScopes = DEFAULT_SYSTEM_TEST_WRITE_SCOPES;
        private List<String> systemTestSmokeSelectors = List.of();
        private String gitUserName = "gitcode-feature-evolver";
        private String gitUserEmail = "gitcode-feature-evolver@localhost";
        private String containerRuntime = "podman";
        private String containerImage = "";
        private Path containerMavenCache;
        private String containerUser = "1000:1000";
        private int containerTimeoutMinutes = 30;
        private ContainerLimits containerLimits = new ContainerLimits(2048, "2.0", 256);
        private int maxPrimaryRepairRounds = 5;
        private int maxDiagnosticRepairRounds = 3;
        private int maxTransientStageRetries = 5;
        private int maxDependencyPrefetchRounds = 2;
        private boolean dependencyPrefetchEnabled = true;
        private Path dependencyPrefetchCacheRoot;
        private int dependencyPrefetchRetentionHours = 24;
        private String modelProvider = "";
        private String modelName = "";
        private String modelApiBase = "";
        private String modelApiKey = "";
        private boolean modelVerifySsl = true;

        private Builder() {
        }

        private Builder(Builder source) {
            this.bindHost = source.bindHost;
            this.port = source.port;
            this.dataDir = source.dataDir;
            this.worktreeRoot = source.worktreeRoot;
            this.localRepository = source.localRepository;
            this.featureSkill = source.featureSkill;
            this.codingStandardSkill = source.codingStandardSkill;
            this.webhookSecret = source.webhookSecret;
            this.gitCodeToken = source.gitCodeToken;
            this.gitCodeUsername = source.gitCodeUsername;
            this.systemTestGitCodeToken = source.systemTestGitCodeToken;
            this.systemTestGitCodeUsername = source.systemTestGitCodeUsername;
            this.triggerMode = source.triggerMode;
            this.triggerLabel = source.triggerLabel;
            this.issueScanWindowHours = source.issueScanWindowHours;
            this.pollIntervalMinutes = source.pollIntervalMinutes;
            this.manualPollingEnabled = source.manualPollingEnabled;
            this.maxIssueScanPages = source.maxIssueScanPages;
            this.defaultWorkflowMode = source.defaultWorkflowMode;
            this.approverLogins = List.copyOf(source.approverLogins);
            this.assignees = List.copyOf(source.assignees);
            this.componentRoot = source.componentRoot;
            this.targetRepository = source.targetRepository;
            this.publishRepository = source.publishRepository;
            this.baseBranch = source.baseBranch;
            this.systemTestEnabled = source.systemTestEnabled;
            this.systemTestRepository = source.systemTestRepository;
            this.systemTestPublishRepository = source.systemTestPublishRepository;
            this.systemTestBaseBranch = source.systemTestBaseBranch;
            this.systemTestWriteScopes = List.copyOf(source.systemTestWriteScopes);
            this.systemTestSmokeSelectors = List.copyOf(source.systemTestSmokeSelectors);
            this.gitUserName = source.gitUserName;
            this.gitUserEmail = source.gitUserEmail;
            this.containerRuntime = source.containerRuntime;
            this.containerImage = source.containerImage;
            this.containerMavenCache = source.containerMavenCache;
            this.containerUser = source.containerUser;
            this.containerTimeoutMinutes = source.containerTimeoutMinutes;
            this.containerLimits = source.containerLimits;
            this.maxPrimaryRepairRounds = source.maxPrimaryRepairRounds;
            this.maxDiagnosticRepairRounds = source.maxDiagnosticRepairRounds;
            this.maxTransientStageRetries = source.maxTransientStageRetries;
            this.maxDependencyPrefetchRounds = source.maxDependencyPrefetchRounds;
            this.dependencyPrefetchEnabled = source.dependencyPrefetchEnabled;
            this.dependencyPrefetchCacheRoot = source.dependencyPrefetchCacheRoot;
            this.dependencyPrefetchRetentionHours = source.dependencyPrefetchRetentionHours;
            this.modelProvider = source.modelProvider;
            this.modelName = source.modelName;
            this.modelApiBase = source.modelApiBase;
            this.modelApiKey = source.modelApiKey;
            this.modelVerifySsl = source.modelVerifySsl;
        }

        /** @param value listener host @return this builder */
        public Builder bindHost(String value) {
            this.bindHost = text(value);
            return this;
        }

        /** @param value listener port @return this builder */
        public Builder port(int value) {
            this.port = value;
            return this;
        }

        /** @param value external data directory @return this builder */
        public Builder dataDir(Path value) {
            this.dataDir = normalize(value);
            return this;
        }

        /** @param value persistent Worktree root @return this builder */
        public Builder worktreeRoot(Path value) {
            this.worktreeRoot = normalize(value);
            return this;
        }

        /** @param value local seed repository @return this builder */
        public Builder localRepository(Path value) {
            this.localRepository = normalize(value);
            return this;
        }

        /** @param value Feature DevFlow Skill directory @return this builder */
        public Builder featureSkill(Path value) {
            this.featureSkill = normalize(value);
            return this;
        }

        /** @param value coding-standard Skill directory @return this builder */
        public Builder codingStandardSkill(Path value) {
            this.codingStandardSkill = normalize(value);
            return this;
        }

        /** @param value Webhook HMAC secret @return this builder */
        public Builder webhookSecret(String value) {
            this.webhookSecret = text(value);
            return this;
        }

        /** @param value feature bot GitCode token @return this builder */
        public Builder gitCodeToken(String value) {
            this.gitCodeToken = text(value);
            return this;
        }

        /** @param value login that owns the GitCode PAT @return this builder */
        public Builder gitCodeUsername(String value) {
            this.gitCodeUsername = text(value);
            return this;
        }

        /** @param value optional isolated test-repository PAT @return this builder */
        public Builder systemTestGitCodeToken(String value) {
            this.systemTestGitCodeToken = text(value);
            return this;
        }

        /** @param value login that owns the isolated test-repository PAT @return this builder */
        public Builder systemTestGitCodeUsername(String value) {
            this.systemTestGitCodeUsername = text(value);
            return this;
        }

        /** @param value trigger mode @return this builder */
        public Builder triggerMode(TriggerMode value) {
            this.triggerMode = value;
            return this;
        }

        /** @param value exact trigger label @return this builder */
        public Builder triggerLabel(String value) {
            this.triggerLabel = text(value);
            return this;
        }

        /** @param value updated-at scan hours @return this builder */
        public Builder issueScanWindowHours(int value) {
            this.issueScanWindowHours = value;
            return this;
        }

        /** @param value fixed polling delay minutes @return this builder */
        public Builder pollIntervalMinutes(int value) {
            this.pollIntervalMinutes = value;
            return this;
        }

        /** @param value enable the loopback-only manual polling endpoint @return this builder */
        public Builder manualPollingEnabled(boolean value) {
            this.manualPollingEnabled = value;
            return this;
        }

        /** @param value maximum Issue pages per scan @return this builder */
        public Builder maxIssueScanPages(int value) {
            this.maxIssueScanPages = value;
            return this;
        }

        /** @param value default workflow mode @return this builder */
        public Builder defaultWorkflowMode(FeatureWorkflowMode value) {
            this.defaultWorkflowMode = value;
            return this;
        }

        /** @param value authenticated approver logins @return this builder */
        public Builder approverLogins(List<String> value) {
            this.approverLogins = copy(value);
            return this;
        }

        /** @param value pull-request assignees @return this builder */
        public Builder assignees(List<String> value) {
            this.assignees = copy(value);
            return this;
        }

        /** @param value repository-relative component root @return this builder */
        public Builder componentRoot(String value) {
            this.componentRoot = text(value);
            return this;
        }

        /** @param value target repository coordinates @return this builder */
        public Builder targetRepository(String value) {
            this.targetRepository = text(value);
            return this;
        }

        /** @param value publication repository coordinates @return this builder */
        public Builder publishRepository(String value) {
            this.publishRepository = text(value);
            return this;
        }

        /** @param value base branch @return this builder */
        public Builder baseBranch(String value) {
            this.baseBranch = text(value);
            return this;
        }

        /** @param value enable post-merge system-test delivery @return this builder */
        public Builder systemTestEnabled(boolean value) {
            this.systemTestEnabled = value;
            return this;
        }

        /** @param value target system-test repository @return this builder */
        public Builder systemTestRepository(String value) {
            this.systemTestRepository = text(value);
            return this;
        }

        /** @param value fork repository used to publish test branches @return this builder */
        public Builder systemTestPublishRepository(String value) {
            this.systemTestPublishRepository = text(value);
            return this;
        }

        /** @param value system-test repository base branch @return this builder */
        public Builder systemTestBaseBranch(String value) {
            this.systemTestBaseBranch = text(value);
            return this;
        }

        /** @param value exact writable test directories @return this builder */
        public Builder systemTestWriteScopes(List<String> value) {
            this.systemTestWriteScopes = copy(value);
            return this;
        }

        /** @param value operator-approved exact smoke test classes @return this builder */
        public Builder systemTestSmokeSelectors(List<String> value) {
            this.systemTestSmokeSelectors = copy(value);
            return this;
        }

        /** @param value Git author name @return this builder */
        public Builder gitUserName(String value) {
            this.gitUserName = text(value);
            return this;
        }

        /** @param value Git author email @return this builder */
        public Builder gitUserEmail(String value) {
            this.gitUserEmail = text(value);
            return this;
        }

        /** @param value rootless container executable @return this builder */
        public Builder containerRuntime(String value) {
            this.containerRuntime = text(value);
            return this;
        }

        /** @param value digest-pinned public image @return this builder */
        public Builder containerImage(String value) {
            this.containerImage = text(value);
            return this;
        }

        /** @param value credential-free Maven cache @return this builder */
        public Builder containerMavenCache(Path value) {
            this.containerMavenCache = normalize(value);
            return this;
        }

        /** @param value non-root UID:GID @return this builder */
        public Builder containerUser(String value) {
            this.containerUser = text(value);
            return this;
        }

        /** @param value gate timeout minutes @return this builder */
        public Builder containerTimeoutMinutes(int value) {
            this.containerTimeoutMinutes = value;
            return this;
        }

        /** @param value immutable container limits @return this builder */
        public Builder containerLimits(ContainerLimits value) {
            this.containerLimits = value;
            return this;
        }

        /** @param value primary Agent repair budget @return this builder */
        public Builder maxPrimaryRepairRounds(int value) {
            this.maxPrimaryRepairRounds = value;
            return this;
        }

        /** @param value diagnostic Agent repair budget @return this builder */
        public Builder maxDiagnosticRepairRounds(int value) {
            this.maxDiagnosticRepairRounds = value;
            return this;
        }

        /** @param value transient stage retry budget @return this builder */
        public Builder maxTransientStageRetries(int value) {
            this.maxTransientStageRetries = value;
            return this;
        }

        /** @param value dependency-prefetch budget @return this builder */
        public Builder maxDependencyPrefetchRounds(int value) {
            this.maxDependencyPrefetchRounds = value;
            return this;
        }

        /** @param value enable isolated dependency prefetch @return this builder */
        public Builder dependencyPrefetchEnabled(boolean value) {
            this.dependencyPrefetchEnabled = value;
            return this;
        }

        /** @param value per-Job dependency-cache root @return this builder */
        public Builder dependencyPrefetchCacheRoot(Path value) {
            this.dependencyPrefetchCacheRoot = normalize(value);
            return this;
        }

        /** @param value terminal Job cache retention hours @return this builder */
        public Builder dependencyPrefetchRetentionHours(int value) {
            this.dependencyPrefetchRetentionHours = value;
            return this;
        }

        /** @param value model provider @return this builder */
        public Builder modelProvider(String value) {
            this.modelProvider = text(value);
            return this;
        }

        /** @param value model name @return this builder */
        public Builder modelName(String value) {
            this.modelName = text(value);
            return this;
        }

        /** @param value model API base @return this builder */
        public Builder modelApiBase(String value) {
            this.modelApiBase = text(value);
            return this;
        }

        /** @param value model API key @return this builder */
        public Builder modelApiKey(String value) {
            this.modelApiKey = text(value);
            return this;
        }

        /** @param value model TLS verification flag @return this builder */
        public Builder modelVerifySsl(boolean value) {
            this.modelVerifySsl = value;
            return this;
        }

        /** @return immutable feature configuration */
        public FeatureEvolvingConfig build() {
            return new FeatureEvolvingConfig(this);
        }

        private static Path normalize(Path path) {
            return path == null ? null : path.toAbsolutePath().normalize();
        }

        private static String text(String value) {
            return value == null ? "" : value.strip();
        }

        private static List<String> copy(List<String> values) {
            return values == null ? List.of() : values.stream().map(Builder::text).toList();
        }
    }
}
