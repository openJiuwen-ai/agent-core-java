/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.FeatureNaming;
import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;
import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_issue_evolver.RepositoryCoordinates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces the post-merge system-test artifact, source, and evidence contract.
 *
 * @since 0.1.12
 */
public final class SystemTestArtifactInspector {
    private static final Pattern PACKAGE = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
    private static final Pattern VERDICT = Pattern.compile("(?im)^\\s*`?(PASS|REWORK)`?\\s*$");
    private static final List<String> FORBIDDEN_TEST_TOKENS = List.of(
            "@Disabled", "Assumptions.", "assumeTrue(", "assumeFalse(", ".sleep(",
            "LockSupport.parkNanos(", "LockSupport.parkUntil(");
    private static final List<String> REQUIRED_EVIDENCE_SECTIONS = List.of(
            "## Identity", "## Scenario Selection", "## API Testability",
            "## Changed Paths and Fixtures", "## Controller Evidence",
            "## SDK Gap / Blocker", "## Review Readiness");
    private static final String APPROVED_GATE_PROFILE =
            "Configured smoke + new system tests";
    private static final Pattern APPROVED_GATE_PROFILE_ROW = Pattern.compile(
            "(?m)^\\s*\\|\\s*" + Pattern.quote(APPROVED_GATE_PROFILE) + "\\s*\\|");
    private static final Pattern RETIRED_COMPILE_PROFILE_ROW = Pattern.compile(
            "(?im)^\\s*\\|\\s*compile\\s*\\(no tests executed\\)\\s*\\|");
    private final Path worktree;
    private final FeatureJob job;
    private final String sourceRevision;
    private final RepositoryCoordinates coordinates;
    private final List<String> configuredScopes;
    private final String artifactRoot;

    /**
     * Bind validation to one test Worktree and its trusted repository identity.
     *
     * @param worktree system-test repository Worktree
     * @param job current feature job
     * @param configuredScopes exact configured test-code/resource scopes
     * @param sourceRevision frozen merged-source revision
     * @param coordinates trusted test target, publication, and base coordinates
     */
    public SystemTestArtifactInspector(Path worktree, FeatureJob job,
                                       List<String> configuredScopes, String sourceRevision,
                                       RepositoryCoordinates coordinates) {
        this.worktree = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        this.job = Objects.requireNonNull(job, "job must not be null");
        this.sourceRevision = requireRevision(sourceRevision);
        this.coordinates = Objects.requireNonNull(
                coordinates, "coordinates must not be null");
        this.configuredScopes = FeaturePathPolicy.normalizeScopes(configuredScopes);
        this.artifactRoot = FeatureNaming.systemTestArtifactRoot(
                job.identity().issue().iid(), job.identity().issue().title());
    }

    /** @return test code/resources plus task-owned evidence directory */
    public List<String> authorWriteScopes() {
        List<String> scopes = new ArrayList<>(configuredScopes);
        scopes.add(artifactRoot + "/");
        return FeaturePathPolicy.normalizeScopes(scopes);
    }

    /** @return normalized task-owned evidence-directory prefix */
    public String artifactWriteScope() {
        return FeaturePathPolicy.normalize(artifactRoot) + "/";
    }

    /** @return one exact independent review-record path */
    public String reviewPath(int round) {
        if (round < 1) {
            throw new IllegalArgumentException("review round must be positive");
        }
        return artifactRoot + "/reviews/system-test-review-r" + round + ".md";
    }

    /**
     * Validate final author output and derive fixed selected-test identifiers.
     *
     * @param dirtyFiles controller-reported test Worktree changes
     * @return validation errors and selected test classes
     */
    public Validation validateAuthor(List<String> dirtyFiles) {
        List<String> normalized = dirtyFiles == null ? List.of()
                : dirtyFiles.stream().map(FeaturePathPolicy::normalize).toList();
        List<String> errors = new ArrayList<>();
        Path evidence = resolve(artifactRoot + "/system-test.md");
        String evidenceText = readOptional(evidence);
        if (evidenceText.isBlank()) {
            errors.add("system-test.md is missing or empty");
        } else {
            REQUIRED_EVIDENCE_SECTIONS.forEach(
                    section -> requireText(evidenceText, section, errors));
            validateIdentity(evidenceText, errors);
            validateControllerEvidenceContract(evidenceText, errors);
        }
        Set<String> selectors = new LinkedHashSet<>();
        for (String relative : normalized) {
            if (!relative.startsWith("src/test/java/") || !relative.endsWith(".java")) {
                continue;
            }
            String source = readOptional(resolve(relative));
            if (!source.contains("@Test") && !source.contains("@ParameterizedTest")) {
                continue;
            }
            String compactSource = source.replaceAll("\\s+", "");
            for (String forbidden : FORBIDDEN_TEST_TOKENS) {
                if (compactSource.contains(forbidden)) {
                    errors.add("test uses forbidden nondeterministic/skip mechanism: " + relative);
                    break;
                }
            }
            selectors.add(selector(relative, source));
        }
        if (selectors.isEmpty()) {
            errors.add("no changed executable Java system test was found");
        }
        return new Validation(errors, List.copyOf(selectors));
    }

    private void validateIdentity(String evidence, List<String> errors) {
        requireText(evidence, job.identity().issue().url(), errors);
        requireText(evidence, job.pullRequest().url(), errors);
        requireText(evidence, sourceRevision, errors);
        requireIdentityRow(evidence, "Test target repository",
                coordinates.targetRepository(), errors);
        requireIdentityRow(evidence, "Test publication repository",
                coordinates.publishRepository(), errors);
        requireIdentityRow(evidence, "Test base branch", coordinates.baseBranch(), errors);
    }

    /** Read a PASS or REWORK verdict from the exact review record. */
    public FeatureArtifactInspector.Verdict verdict(String reviewPath) {
        String review = readRequired(resolve(reviewPath));
        int heading = review.toUpperCase(Locale.ROOT).indexOf("## VERDICT");
        if (heading < 0) {
            throw new IllegalStateException("System-test review has no verdict section");
        }
        String section = review.substring(heading, Math.min(review.length(), heading + 300));
        Matcher matcher = VERDICT.matcher(section);
        if (!matcher.find()) {
            throw new IllegalStateException("System-test review has no supported verdict");
        }
        return "REWORK".equalsIgnoreCase(matcher.group(1)) || hasOpenBlockingFinding(review)
                ? FeatureArtifactInspector.Verdict.REWORK : FeatureArtifactInspector.Verdict.PASS;
    }

    /** @return bounded durable system-test evidence for the next Agent invocation */
    public String currentEvidence() {
        String evidence = readOptional(resolve(artifactRoot + "/system-test.md"));
        int maximum = 8000;
        return evidence.length() <= maximum
                ? evidence : evidence.substring(evidence.length() - maximum);
    }

    /** Append real controller-owned container evidence to system-test.md. */
    public void appendEvidence(String profile, ContainerGateResult result, String testRevision) {
        Path evidence = resolve(artifactRoot + "/system-test.md");
        if (!Files.isRegularFile(evidence) || Files.isSymbolicLink(evidence)) {
            throw new IllegalStateException("system-test.md is unavailable for controller evidence");
        }
        String output = result.output().replace('\r', ' ').replace('\n', ' ').strip();
        output = output.substring(0, Math.min(output.length(), 1000));
        String line = System.lineSeparator() + "- " + Instant.now() + " | " + profile
                + " | " + result.outcome() + " | exit=" + result.exitCode()
                + " | source=" + safe(sourceRevision)
                + " | test=" + safe(testRevision) + " | " + output + System.lineSeparator();
        try {
            String content = Files.readString(evidence, StandardCharsets.UTF_8);
            if (!content.contains("## Controller Evidence Log")) {
                Files.writeString(evidence,
                        System.lineSeparator() + "## Controller Evidence Log" + System.lineSeparator(),
                        StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
            Files.writeString(evidence, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to persist system-test evidence", ex);
        }
    }

    private static String selector(String relative, String source) {
        String fileName = relative.substring(relative.lastIndexOf('/') + 1, relative.length() - 5);
        Matcher packageName = PACKAGE.matcher(source);
        return packageName.find() ? packageName.group(1) + "." + fileName : fileName;
    }

    private Path resolve(String relative) {
        Path resolved = worktree.resolve(FeaturePathPolicy.normalize(relative)).normalize();
        if (!resolved.startsWith(worktree)) {
            throw new IllegalArgumentException("System-test path escapes the Worktree");
        }
        return resolved;
    }

    private static String readOptional(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                return "";
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read system-test file", ex);
        }
    }

    private static String readRequired(Path file) {
        String content = readOptional(file);
        if (content.isBlank()) {
            throw new IllegalStateException("System-test review record is unavailable");
        }
        return content;
    }

    private static void requireText(String content, String required, List<String> errors) {
        if (required == null || required.isBlank() || !content.contains(required)) {
            errors.add("system-test.md is missing required identity/evidence: " + required);
        }
    }

    private static void requireIdentityRow(String content, String field, String value,
                                           List<String> errors) {
        Pattern row = Pattern.compile("(?m)^\\s*\\|\\s*" + Pattern.quote(field)
                + "\\s*\\|\\s*`?" + Pattern.quote(value) + "`?\\s*\\|\\s*$");
        if (!row.matcher(content).find()) {
            errors.add("system-test.md is missing trusted identity row: "
                    + field + " = " + value);
        }
    }

    private static void validateControllerEvidenceContract(String content,
                                                             List<String> errors) {
        int approvedProfiles = countMatches(APPROVED_GATE_PROFILE_ROW, content);
        if (approvedProfiles != 1) {
            errors.add("system-test.md must contain exactly one approved controller profile: "
                    + APPROVED_GATE_PROFILE);
        }
        if (RETIRED_COMPILE_PROFILE_ROW.matcher(content).find()) {
            errors.add("system-test.md contains the retired compile-only evidence profile; "
                    + "remove it because the controller runs only smoke plus new tests");
        }
    }

    private static int countMatches(Pattern pattern, String content) {
        int count = 0;
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static boolean hasOpenBlockingFinding(String review) {
        for (String line : review.split("\\R")) {
            if (!line.strip().startsWith("|")) {
                continue;
            }
            String[] columns = line.toLowerCase(Locale.ROOT).split("\\|", -1);
            if (columns.length <= 8) {
                continue;
            }
            String severity = columns[2].strip();
            String resolution = columns[8].strip();
            boolean blocking = "critical".equals(severity) || "important".equals(severity);
            boolean resolved = resolution.startsWith("resolved")
                    || resolution.startsWith("verified") || resolution.startsWith("closed");
            if (blocking && !resolved) {
                return true;
            }
        }
        return false;
    }

    private static String safe(String value) {
        String text = value == null || value.isBlank() ? "N/A" : value.strip();
        return text.replace('\r', ' ').replace('\n', ' ')
                .substring(0, Math.min(text.length(), 100));
    }

    private static String requireRevision(String revision) {
        String required = revision == null ? "" : revision.strip();
        if (!required.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("merged source revision is invalid");
        }
        return required;
    }

    /** Final author-validation result. */
    public record Validation(List<String> errors, List<String> testSelectors) {
        /** Freeze validation data. */
        public Validation {
            errors = errors == null ? List.of() : List.copyOf(errors);
            testSelectors = testSelectors == null ? List.of() : List.copyOf(testSelectors);
        }

        /** @return whether the system-test output satisfies static policy */
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
