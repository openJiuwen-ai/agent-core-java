/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.profile;

import examples.gitcode_issue_evolver.RepositoryCoordinates;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Java path, impact, branch, and verification policy for a configured agent-core-java repository.
 *
 * @since 0.1.12
 */
public final class AgentCoreJavaRepositoryProfile implements RepositoryProfile {
    private static final List<String> ALLOWED_PREFIXES = List.of("src/main/java/", "src/test/java/");
    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "examples/", "src/main/java/examples/");
    private static final Pattern SENSITIVE_SEGMENT = Pattern.compile(
            "(^|/)(auth|authentication|security)(/|$)");

    private static final Pattern DRIVE_PATH_PATTERN = Pattern.compile("^[A-Za-z]:/.*");
    private static final Pattern DIACRITIC_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHEN_PATTERN = Pattern.compile("^-+|-+$");
    private static final Pattern TRAILING_HYPHEN_PATTERN = Pattern.compile("-+$");
    private static final Pattern CURRENT_DIRECTORY_PATTERN = Pattern.compile("^\\./");
    private final String repository;
    private final String baseBranch;

    /**
     * Create the backward-compatible production repository profile.
     */
    public AgentCoreJavaRepositoryProfile() {
        this(RepositoryCoordinates.defaults());
    }

    /**
     * Create a profile for the configured target repository and baseline branch.
     *
     * @param coordinates validated service repository coordinates
     */
    public AgentCoreJavaRepositoryProfile(RepositoryCoordinates coordinates) {
        RepositoryCoordinates requiredCoordinates = Objects.requireNonNull(
                coordinates, "coordinates must not be null");
        this.repository = requiredCoordinates.targetRepository();
        this.baseBranch = requiredCoordinates.baseBranch();
    }

    @Override
    public String repository() {
        return repository;
    }

    @Override
    public String baseBranch() {
        return baseBranch;
    }

    @Override
    public ChangeValidation validateChanges(Collection<String> changedFiles) {
        List<String> violations = new ArrayList<>();
        if (changedFiles != null) {
            for (String changedFile : changedFiles) {
                String path = normalizePath(changedFile);
                if (path.isBlank() || isUnsafe(changedFile) || !isAllowed(path)) {
                    violations.add(path.isBlank() ? "<empty>" : path);
                }
            }
        }
        return new ChangeValidation(violations.isEmpty(), isHighImpact(changedFiles), violations);
    }

    @Override
    public boolean isHighImpact(Collection<String> changedFiles) {
        if (changedFiles == null) {
            return false;
        }
        return changedFiles.stream().map(AgentCoreJavaRepositoryProfile::normalizePath)
                .anyMatch(this::isSensitive);
    }

    @Override
    public VerificationPlan verificationPlan() {
        return new VerificationPlan(List.of(List.of("mvn", "-B", "-ntp", "-DskipTests", "test-compile")),
                Duration.ofMinutes(20), 2);
    }

    @Override
    public String branchName(long issueIid, String issueTitle) {
        String ascii = Normalizer.normalize(issueTitle == null ? "" : issueTitle, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        ascii = DIACRITIC_PATTERN.matcher(ascii).replaceAll("");
        ascii = NON_SLUG_PATTERN.matcher(ascii).replaceAll("-");
        ascii = EDGE_HYPHEN_PATTERN.matcher(ascii).replaceAll("");
        String slug = ascii.isBlank() ? "change" : TRAILING_HYPHEN_PATTERN.matcher(
                ascii.substring(0, Math.min(40, ascii.length()))).replaceAll("");
        return "auto-evolving/issue-" + issueIid + "-" + (slug.isBlank() ? "change" : slug);
    }

    private boolean isAllowed(String path) {
        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isSensitive(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if ("pom.xml".equals(lower) || lower.startsWith(".github/") || lower.startsWith(".gitcode/")) {
            return true;
        }
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        return SENSITIVE_SEGMENT.matcher(lower).find()
                || fileName.contains("auth") || fileName.contains("security");
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String normalized = CURRENT_DIRECTORY_PATTERN.matcher(path.replace('\\', '/')).replaceAll("");
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    private static boolean isUnsafe(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || DRIVE_PATH_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        return List.of(normalized.split("/")).contains("..");
    }
}
