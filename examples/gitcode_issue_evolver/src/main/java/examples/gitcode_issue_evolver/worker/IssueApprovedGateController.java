/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import com.openjiuwen.autoharness.infra.GitOperations;
import examples.gitcode_issue_evolver.infrastructure.CIGateResult;
import examples.gitcode_issue_evolver.infrastructure.CIGateRunner;
import examples.gitcode_issue_evolver.infrastructure.VerificationFailureType;
import examples.gitcode_issue_evolver.job.IssueGateReceipt;
import examples.gitcode_issue_evolver.profile.ChangeValidation;
import examples.gitcode_issue_evolver.profile.RepositoryProfile;
import examples.gitcode_issue_evolver.profile.VerificationPlan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** Controller-owned, immutable-policy verification for one Issue repair lifecycle. */
final class IssueApprovedGateController {
    private static final int MAX_OUTPUT_TEXT = 6_000;
    private final Path worktree;
    private final GitOperations git;
    private final RepositoryProfile profile;
    private final VerificationPlan plan;
    private final IssueSmokeTestRunner smoke;
    private final Runnable smokeListener;
    private final Consumer<Receipt> receiptListener;
    private final Function<String, Optional<IssueGateReceipt>> receiptLookup;
    private final Map<String, Receipt> cache = new LinkedHashMap<>();
    private Receipt latest;

    IssueApprovedGateController(Path worktree, GitOperations git,
                                RepositoryProfile profile, VerificationPlan plan,
                                IssueSmokeTestRunner smoke, Runnable smokeListener,
                                Consumer<Receipt> receiptListener,
                                Function<String, Optional<IssueGateReceipt>> receiptLookup) {
        this.worktree = Objects.requireNonNull(worktree, "worktree must not be null");
        this.git = Objects.requireNonNull(git, "git must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.smoke = Objects.requireNonNull(smoke, "smoke must not be null");
        this.smokeListener = Objects.requireNonNull(
                smokeListener, "smokeListener must not be null");
        this.receiptListener = Objects.requireNonNull(
                receiptListener, "receiptListener must not be null");
        this.receiptLookup = Objects.requireNonNull(receiptLookup, "receiptLookup must not be null");
    }

    synchronized Map<String, Object> runForAgent() {
        return run().response(profileName());
    }

    String profileName() {
        return smoke.isEnabled() ? "TARGETED_SMOKE" : "TARGETED";
    }

    synchronized Receipt run() {
        List<String> changedFiles = List.copyOf(git.listDirtyFiles());
        String fingerprint = fingerprint(changedFiles);
        Receipt cached = cache.get(fingerprint);
        if (cached == null) {
            cached = receiptLookup.apply(fingerprint)
                    .filter(receipt -> !"TRANSIENT".equals(receipt.status()))
                    .map(IssueApprovedGateController::fromDurable)
                    .orElse(null);
            if (cached != null) {
                cache.put(fingerprint, cached);
            }
        }
        if (cached != null) {
            latest = cached.withCached(true);
            receiptListener.accept(latest);
            return latest;
        }
        Receipt receipt = execute(fingerprint, changedFiles);
        if (receipt.status() != Status.TRANSIENT) {
            cache.put(fingerprint, receipt);
        }
        latest = receipt;
        receiptListener.accept(latest);
        return receipt;
    }

    private static Receipt fromDurable(IssueGateReceipt receipt) {
        return new Receipt(Status.valueOf(receipt.status()), receipt.fingerprint(), true,
                receipt.code(), receipt.category(), receipt.code().isBlank()
                ? "Gate passed" : "Reused deterministic Gate result",
                receipt.exitCode(), receipt.outputTail(),
                Instant.ofEpochMilli(receipt.completedAt()));
    }

    synchronized Receipt latest() {
        return latest;
    }

    private Receipt execute(String fingerprint, List<String> changedFiles) {
        if (changedFiles.isEmpty()) {
            return Receipt.failed(fingerprint, "AGENT_FAILED_TO_ACT", "AGENT_CORRECTABLE",
                    "No repository change was produced", "Inspect the reported target and implement a bounded fix");
        }
        ChangeValidation validation = profile.validateChanges(changedFiles);
        if (!validation.allowed()) {
            return Receipt.failed(fingerprint, "WRITE_SCOPE_VIOLATION", "POLICY_VIOLATION",
                    "Repository changes exceeded the approved Java scope",
                    String.join(", ", validation.violations()));
        }
        CIGateResult gate = new CIGateRunner(worktree.toString(),
                plan.commands(), plan.timeout()).run();
        String output = safeGateError(gate);
        if (!gate.isPassed()) {
            return compileFailure(fingerprint, gate.resolvedFailureType(), output);
        }
        if (!smoke.isEnabled()) {
            return new Receipt(Status.PASSED, fingerprint, false, "", "", "Gate passed",
                    0, output, Instant.now());
        }
        smokeListener.run();
        return smokeReceipt(fingerprint, smoke.run(worktree));
    }

    private static Receipt compileFailure(String fingerprint, VerificationFailureType type,
                                          String output) {
        if (type.isInfrastructureFailure()) {
            return new Receipt(Status.TRANSIENT, fingerprint, false,
                    "CI_INFRASTRUCTURE_FAILED", "TRANSIENT_INFRASTRUCTURE",
                    "Trusted verification infrastructure failed", 1, output, Instant.now());
        }
        return new Receipt(Status.FAILED, fingerprint, false,
                "VERIFICATION_FAILED", "AGENT_CORRECTABLE",
                "Trusted Java verification failed", 1, output, Instant.now());
    }

    private static Receipt smokeReceipt(String fingerprint, IssueSmokeTestRunner.Result result) {
        if (result.status() == IssueSmokeTestRunner.Status.PASSED) {
            return new Receipt(Status.PASSED, fingerprint, false, "", "",
                    "Compile and smoke Gates passed", 0, result.output(), Instant.now());
        }
        Status status = result.status() == IssueSmokeTestRunner.Status.TRANSIENT
                ? Status.TRANSIENT : Status.FAILED;
        return new Receipt(status, fingerprint, false, result.code(), result.category(),
                "JiuwenTestJava smoke Gate failed", 1, result.output(), Instant.now());
    }

    private String fingerprint(List<String> changedFiles) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "profile=" + profileName() + System.lineSeparator());
            update(digest, "smoke=" + smoke.fingerprint() + System.lineSeparator());
            update(digest, "head=" + git.currentHead() + System.lineSeparator());
            for (List<String> command : plan.commands()) {
                update(digest, String.join("\u0000", command));
                update(digest, System.lineSeparator());
            }
            List<String> ordered = new ArrayList<>(changedFiles);
            ordered.sort(String::compareTo);
            for (String path : ordered) {
                update(digest, path);
                update(digest, "\u0000");
                Path file = worktree.resolve(path).normalize();
                if (file.startsWith(worktree) && Files.isRegularFile(file)) {
                    digest.update(Files.readAllBytes(file));
                } else {
                    update(digest, "[deleted]");
                }
            }
            fingerprintBuildPolicy(digest, worktree, worktree.resolve("pom.xml"));
            fingerprintBuildPolicy(digest, worktree, worktree.resolve(".mvn"));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to fingerprint Issue Gate inputs", ex);
        }
    }

    private static void fingerprintBuildPolicy(MessageDigest digest, Path root, Path path)
            throws IOException {
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            update(digest, worktreeRelativeLabel(root, path));
            digest.update(Files.readAllBytes(path));
            return;
        }
        if (!Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> files = Files.walk(path)) {
            for (Path file : files.filter(file -> Files.isRegularFile(
                    file, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                update(digest, worktreeRelativeLabel(root, file));
                digest.update(Files.readAllBytes(file));
            }
        }
    }

    private static String worktreeRelativeLabel(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeGateError(CIGateResult gate) {
        String error = gate.getErrors();
        if (error == null || error.isBlank()) {
            error = String.join("\n", gate.getGateOutputs() == null ? List.of() : gate.getGateOutputs());
        }
        String normalized = error == null ? "" : error.strip();
        return normalized.substring(Math.max(0, normalized.length() - MAX_OUTPUT_TEXT));
    }

    enum Status {
        PASSED,
        FAILED,
        TRANSIENT
    }

    record Receipt(Status status, String fingerprint, boolean cached,
                   String code, String category, String summary,
                   int exitCode, String outputTail, Instant completedAt) {
        private static Receipt failed(String fingerprint, String code, String category,
                                      String summary, String output) {
            return new Receipt(Status.FAILED, fingerprint, false, code, category,
                    summary, 1, output, Instant.now());
        }

        private Receipt withCached(boolean cached) {
            return new Receipt(status, fingerprint, cached, code, category,
                    summary, exitCode, outputTail, completedAt);
        }

        Map<String, Object> response(String profile) {
            Map<String, Object> failure = code.isBlank() ? null : Map.of(
                    "code", code, "category", category, "summary", summary,
                    "repairHints", outputTail.isBlank() ? List.of() : List.of(outputTail));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", status.name());
            response.put("stage", "BUGFIX");
            response.put("profile", profile);
            response.put("fingerprint", fingerprint);
            response.put("cached", cached);
            response.put("failure", failure);
            response.put("evidence", Map.of("exitCode", exitCode, "outputTail", outputTail));
            return response;
        }

        String repairFeedback() {
            return "code=" + code + "\ncategory=" + category + "\nsummary=" + summary
                    + "\ngateFingerprint=" + fingerprint + "\ncached=" + cached
                    + "\nevidence=\n" + outputTail;
        }
    }
}
