/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.workflow;

import examples.gitcode_feature_evolver.agent.FeaturePathPolicy;
import examples.gitcode_feature_evolver.infrastructure.ContainerGateResult;
import examples.gitcode_feature_evolver.job.FeatureJob;
import examples.gitcode_feature_evolver.job.FeatureStage;

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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates DevFlow artifacts, extracts R2-approved paths, and persists controller evidence.
 *
 * @since 0.1.12
 */
public final class FeatureArtifactInspector {
    private static final Pattern TASK_HEADING = Pattern.compile("(?m)^###\\s+(T-[A-Za-z0-9-]+)\\s+.*$");
    private static final Pattern TASK_STATUS = Pattern.compile(
            "(?im)^-\\s*Status:\\s*`?(pending|red|green|refactor|done|blocked)`?\\s*$");
    private static final Pattern TASK_FIELD = Pattern.compile("^-\\s*([^:]+):.*$");
    private static final Pattern TEST_ARGUMENT = Pattern.compile("-Dtest=([^\\s`]+)");
    private static final Pattern TEST_SELECTOR = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern VERDICT = Pattern.compile("(?im)^\\s*`?(PASS|REWORK)`?\\s*$");
    private final Path worktree;
    private final FeatureJob job;
    private final String componentRoot;

    /**
     * Bind artifact operations to one owned Worktree and job.
     *
     * @param worktree persistent feature Worktree
     * @param job current job snapshot
     * @param componentRoot configured repository-relative component root
     */
    public FeatureArtifactInspector(Path worktree, FeatureJob job, String componentRoot) {
        this.worktree = Objects.requireNonNull(worktree, "worktree must not be null")
                .toAbsolutePath().normalize();
        this.job = Objects.requireNonNull(job, "job must not be null");
        this.componentRoot = componentRoot == null ? "." : componentRoot;
    }

    /** @return artifact directory scope available to author stages */
    public List<String> artifactWriteScope() {
        return List.of(job.identity().artifactRoot() + "/");
    }

    /**
     * Return one exact new review-record path for a gate and round.
     *
     * @param gate review stage
     * @param round one-based round
     * @return exact repository-relative review file
     */
    public String reviewPath(FeatureStage gate, int round) {
        String gateName = switch (gate) {
            case REVIEW_R1 -> "r1-spec-review";
            case REVIEW_R2 -> "r2-design-review";
            case REVIEW_R3 -> "r3-code-review";
            default -> throw new IllegalArgumentException("Stage is not a review gate");
        };
        return job.identity().artifactRoot() + "/reviews/" + gateName + "-r" + round + ".md";
    }

    /**
     * Validate mandatory author artifacts for one completed stage.
     *
     * @param stage completed author stage
     * @return non-sensitive validation errors
     */
    public List<String> validateArtifacts(FeatureStage stage) {
        List<String> required = switch (stage) {
            case SPECIFY -> List.of("spec.md", "traceability.md", "plan.md");
            case DESIGN -> List.of("spec.md", "traceability.md", "design.md", "plan.md");
            case SHIP -> List.of("spec.md", "traceability.md", "design.md", "plan.md", "closeout.md");
            default -> List.of();
        };
        List<String> errors = new ArrayList<>();
        for (String name : required) {
            Path file = artifactFile(name);
            try {
                if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)
                        || Files.size(file) == 0L) {
                    errors.add("required artifact is missing: " + name);
                }
            } catch (IOException ex) {
                errors.add("required artifact is unreadable: " + name);
            }
        }
        return List.copyOf(errors);
    }

    /**
     * Parse exact file/directory paths approved by the R2 design table.
     *
     * @return normalized approved write scopes
     */
    public List<String> implementationScopes() {
        List<String> lines = readLines(artifactFile("design.md"));
        boolean inBoundary = false;
        List<String> scopes = new ArrayList<>();
        for (String line : lines) {
            if (line.strip().equalsIgnoreCase("## Implementation Boundary")) {
                inBoundary = true;
                continue;
            }
            if (inBoundary && line.startsWith("## ")) {
                break;
            }
            if (inBoundary) {
                parseBoundaryRow(line).ifPresent(scopes::add);
            }
        }
        if (scopes.isEmpty()) {
            throw new IllegalStateException("R2 design has no approved implementation paths");
        }
        return FeaturePathPolicy.normalizeScopes(scopes);
    }

    /**
     * Select dynamic write scopes for one TDD phase.
     *
     * @param stage RED, GREEN, or REFACTOR
     * @return artifact plus R2-approved code scopes
     */
    public List<String> tddWriteScopes(FeatureStage stage) {
        List<String> scopes = new ArrayList<>(artifactWriteScope());
        for (String scope : implementationScopes()) {
            if (stage != FeatureStage.IMPLEMENT_RED || isTestPath(scope)) {
                scopes.add(scope);
            }
        }
        if (stage == FeatureStage.IMPLEMENT_RED && scopes.size() == 1) {
            throw new IllegalStateException("R2 design has no approved test path for RED");
        }
        return FeaturePathPolicy.normalizeScopes(scopes);
    }

    /** @return artifact and R2-approved long-term component-doc scopes for SHIP */
    public List<String> shipWriteScopes() {
        List<String> scopes = new ArrayList<>(artifactWriteScope());
        String prefix = ".".equals(componentRoot) ? "" : componentRoot + "/";
        scopes.add(prefix + "docs/");
        for (String scope : implementationScopes()) {
            if (isDocumentationPath(scope)) {
                scopes.add(scope);
            }
        }
        return FeaturePathPolicy.normalizeScopes(scopes);
    }

    private static boolean isDocumentationPath(String scope) {
        String normalized = scope.toLowerCase(Locale.ROOT);
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.equals("docs") || normalized.startsWith("docs/")
                || normalized.contains("/docs/") || normalized.endsWith("/docs")
                || normalized.equals("documents") || normalized.startsWith("documents/")
                || normalized.contains("/documents/") || normalized.endsWith("/documents");
    }

    /**
     * Read the next non-done plan task.
     *
     * @return plan cursor; blocked when multiple in-progress tasks exist
     */
    public PlanCursor nextTask() {
        List<Task> tasks = planTasks();
        List<Task> incomplete = tasks.stream().filter(task -> !"done".equals(task.status())).toList();
        return incomplete.stream().findFirst()
                .map(task -> new PlanCursor(task.id(), task.status(), false, tasks.size()))
                .orElseGet(() -> new PlanCursor("", "done", true, tasks.size()));
    }

    /**
     * Validate that source TDD tasks precede deferred delivery tasks and use exact selectors.
     */
    public void validateVerificationPlan() {
        boolean hasTddTask = false;
        boolean hasDeferredTask = false;
        for (Task task : planTasks()) {
            TestSelectorContract contract = task.testSelectors();
            if (contract.red().isEmpty()) {
                if (contract.hasAny()) {
                    throw new IllegalStateException(
                            "A plan task defines GREEN/REFACTOR selectors without a RED selector");
                }
                hasDeferredTask = true;
                continue;
            }
            if (hasDeferredTask) {
                throw new IllegalStateException(
                        "Source TDD tasks must precede deferred delivery tasks in plan.md");
            }
            hasTddTask = true;
        }
        if (!hasTddTask) {
            throw new IllegalStateException("plan.md has no task with an exact RED test selector");
        }
    }

    /**
     * Return the immutable selector contract for one approved plan task.
     *
     * @param taskId stable plan task ID
     * @return exact RED/GREEN/REFACTOR selectors
     */
    public TestSelectorContract verificationContract(String taskId) {
        return planTasks().stream().filter(task -> task.id().equals(taskId)).findFirst()
                .map(Task::testSelectors)
                .orElseThrow(() -> new IllegalStateException("Plan task is unavailable: " + taskId));
    }

    /**
     * Resolve exact controller-approved selectors for one task phase.
     *
     * @param taskId stable plan task ID
     * @param phase RED, GREEN, or REFACTOR verification phase
     * @return deduplicated exact Java test classes
     */
    public List<String> testSelectors(String taskId, TestPhase phase) {
        Objects.requireNonNull(phase, "phase must not be null");
        return verificationContract(taskId).forPhase(phase);
    }

    /**
     * Resolve all approved source selectors for the final targeted gate.
     *
     * @return deduplicated exact Java test classes
     */
    public List<String> allTestSelectors() {
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        for (Task task : planTasks()) {
            selectors.addAll(task.testSelectors().forPhase(TestPhase.REFACTOR));
        }
        return List.copyOf(selectors);
    }

    /**
     * Determine whether another source TDD task remains before deferred delivery work.
     *
     * @return true when the first remaining task has an exact RED selector
     */
    public boolean hasPendingTddTask() {
        boolean hasDeferredTask = false;
        for (Task task : planTasks()) {
            if ("done".equals(task.status())) {
                continue;
            }
            if (task.testSelectors().red().isEmpty()) {
                hasDeferredTask = true;
                continue;
            }
            if (hasDeferredTask) {
                throw new IllegalStateException(
                        "Source TDD tasks must precede deferred delivery tasks in plan.md");
            }
            return true;
        }
        return false;
    }

    /**
     * Return the last sequentially completed task for publication recovery.
     *
     * @return last done task ID, or empty before any task completes
     */
    public Optional<String> lastDoneTaskId() {
        List<Task> tasks = planTasks();
        String last = "";
        for (Task task : tasks) {
            if (!"done".equals(task.status())) {
                break;
            }
            last = task.id();
        }
        return last.isBlank() ? Optional.empty() : Optional.of(last);
    }

    /**
     * Persist the controller-confirmed phase for one plan task.
     *
     * @param taskId exact task ID
     * @param status controller-confirmed task status
     */
    public void recordTaskStatus(String taskId, String status) {
        String requiredTaskId = Objects.requireNonNull(taskId, "taskId must not be null");
        String requiredStatus = Objects.requireNonNull(status, "status must not be null");
        if (!List.of("red", "green", "refactor", "done").contains(requiredStatus)) {
            throw new IllegalArgumentException("Unsupported controller task status");
        }
        Path plan = artifactFile("plan.md");
        String content = read(plan);
        List<Heading> headings = taskHeadings(content);
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            if (headings.get(index).id().equals(requiredTaskId)) {
                matches.add(index);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("plan.md must contain exactly one requested task");
        }
        int taskIndex = matches.get(0);
        int start = headings.get(taskIndex).start();
        int end = taskIndex + 1 < headings.size()
                ? headings.get(taskIndex + 1).start() : content.length();
        String block = content.substring(start, end);
        Matcher taskStatus = TASK_STATUS.matcher(block);
        if (!taskStatus.find()) {
            throw new IllegalStateException("plan.md task has no exact status");
        }
        int statusStart = taskStatus.start();
        int statusEnd = taskStatus.end();
        if (taskStatus.find()) {
            throw new IllegalStateException("plan.md task has multiple status lines");
        }
        String replacement = "- Status: `" + requiredStatus + "`";
        String updatedBlock = block.substring(0, statusStart) + replacement
                + block.substring(statusEnd);
        try {
            Files.writeString(plan, content.substring(0, start) + updatedBlock
                    + content.substring(end), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to persist controller task status", ex);
        }
    }

    private List<Task> planTasks() {
        String plan = read(artifactFile("plan.md"));
        List<Heading> found = taskHeadings(plan);
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < found.size(); index++) {
            Heading heading = found.get(index);
            int end = index + 1 < found.size() ? found.get(index + 1).start() : plan.length();
            String block = plan.substring(heading.start(), end);
            Matcher status = TASK_STATUS.matcher(block);
            String taskStatus = status.find() ? status.group(1) : "missing";
            tasks.add(new Task(heading.id(), taskStatus, parseTestSelectors(block)));
        }
        if (tasks.stream().anyMatch(task -> "missing".equals(task.status()))) {
            throw new IllegalStateException("plan.md contains a task without one exact status");
        }
        long active = tasks.stream().filter(task -> List.of("red", "green", "refactor")
                .contains(task.status())).count();
        if (active > 1) {
            throw new IllegalStateException("plan.md contains multiple active tasks");
        }
        List<Task> incomplete = tasks.stream().filter(task -> !"done".equals(task.status())).toList();
        if (active == 1 && !List.of("red", "green", "refactor")
                .contains(incomplete.get(0).status())) {
            throw new IllegalStateException("plan.md active task is not the single next task");
        }
        return List.copyOf(tasks);
    }

    private static List<Heading> taskHeadings(String plan) {
        Matcher headings = TASK_HEADING.matcher(plan);
        List<Heading> found = new ArrayList<>();
        while (headings.find()) {
            found.add(new Heading(headings.group(1), headings.start()));
        }
        return List.copyOf(found);
    }

    private static TestSelectorContract parseTestSelectors(String taskBlock) {
        LinkedHashSet<String> red = new LinkedHashSet<>();
        LinkedHashSet<String> green = new LinkedHashSet<>();
        LinkedHashSet<String> refactor = new LinkedHashSet<>();
        PlanField currentField = PlanField.NONE;
        for (String line : taskBlock.split("\\R")) {
            Matcher field = TASK_FIELD.matcher(line);
            if (field.matches()) {
                currentField = PlanField.from(field.group(1));
            }
            switch (currentField) {
                case RED -> addTestSelectors(line, red);
                case GREEN -> addTestSelectors(line, green);
                case REFACTOR -> addTestSelectors(line, refactor);
                case NONE -> {
                    // The current task field does not define a controller test selector.
                }
            }
        }
        return new TestSelectorContract(List.copyOf(red), List.copyOf(green),
                List.copyOf(refactor));
    }

    private static void addTestSelectors(String line, LinkedHashSet<String> target) {
        Matcher argument = TEST_ARGUMENT.matcher(line);
        while (argument.find()) {
            String value = argument.group(1);
            for (String selector : value.split(",", -1)) {
                if (!TEST_SELECTOR.matcher(selector).matches()) {
                    throw new IllegalStateException(
                            "plan.md contains a non-exact Maven test selector");
                }
                target.add(selector);
            }
        }
    }

    /**
     * Read a gate verdict from the exact controller-allocated review file.
     *
     * @param reviewPath repository-relative review record
     * @return PASS or REWORK
     */
    public Verdict verdict(String reviewPath) {
        String review = read(resolve(reviewPath));
        String upper = review.toUpperCase(Locale.ROOT);
        int heading = upper.indexOf("## VERDICT");
        if (heading < 0) {
            throw new IllegalStateException("Review record has no verdict section");
        }
        String section = review.substring(heading, Math.min(review.length(), heading + 300));
        Matcher verdict = VERDICT.matcher(section);
        if (!verdict.find()) {
            throw new IllegalStateException("Review record has no exact supported verdict");
        }
        if ("REWORK".equalsIgnoreCase(verdict.group(1)) || hasOpenBlockingFinding(review)) {
            return Verdict.REWORK;
        }
        return Verdict.PASS;
    }

    /** @return bounded tail of durable plan evidence for the next Agent invocation */
    public String currentEvidence() {
        String plan = read(artifactFile("plan.md"));
        int maximum = 8000;
        return plan.length() <= maximum ? plan : plan.substring(plan.length() - maximum);
    }

    /**
     * Append actual controller-owned verification evidence to the durable plan.
     *
     * @param phase RED, GREEN, REFACTOR, or FINAL
     * @param result bounded real command result
     * @param commitAnchor commit SHA or N/A
     */
    public void appendEvidence(String phase, ContainerGateResult result, String commitAnchor) {
        Path plan = artifactFile("plan.md");
        String output = result.output().replace('\r', ' ').replace('\n', ' ').strip();
        output = output.substring(0, Math.min(output.length(), 1000));
        String line = "\n- " + Instant.now() + " | " + phase + " | " + result.outcome()
                + " | exit=" + result.exitCode() + " | commit=" + safe(commitAnchor)
                + " | " + output + "\n";
        try {
            if (!Files.isRegularFile(plan) || Files.isSymbolicLink(plan)) {
                throw new IllegalStateException("plan.md is unavailable for controller evidence");
            }
            String content = Files.readString(plan, StandardCharsets.UTF_8);
            if (!content.contains("## Controller Evidence Log")) {
                Files.writeString(plan, "\n## Controller Evidence Log\n", StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND);
            }
            Files.writeString(plan, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to persist controller verification evidence", ex);
        }
    }

    private java.util.Optional<String> parseBoundaryRow(String line) {
        String stripped = line.strip();
        if (!stripped.startsWith("|") || !stripped.endsWith("|")) {
            return java.util.Optional.empty();
        }
        String[] columns = stripped.substring(1, stripped.length() - 1).split("\\|", -1);
        if (columns.length < 2) {
            return java.util.Optional.empty();
        }
        String candidate = columns[0].strip().replace("`", "");
        if (candidate.isBlank() || candidate.equalsIgnoreCase("Allowed path")
                || candidate.matches("[-: ]+") || candidate.contains("*")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(candidate);
    }

    private Path artifactFile(String name) {
        return resolve(job.identity().artifactRoot() + "/" + name);
    }

    private Path resolve(String relative) {
        String normalized = FeaturePathPolicy.normalize(relative);
        Path resolved = worktree.resolve(normalized).normalize();
        if (!resolved.startsWith(worktree)) {
            throw new IllegalArgumentException("Artifact path escapes the Worktree");
        }
        return resolved;
    }

    private static boolean isTestPath(String scope) {
        String lower = scope.toLowerCase(Locale.ROOT);
        return lower.startsWith("src/test/") || lower.contains("/src/test/")
                || lower.startsWith("test/") || lower.contains("/test/");
    }

    private static String read(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IllegalStateException("DevFlow artifact is not a regular file");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read DevFlow artifact", ex);
        }
    }

    private static List<String> readLines(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IllegalStateException("DevFlow design is not a regular file");
            }
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read DevFlow design", ex);
        }
    }

    private static String safe(String value) {
        String text = value == null || value.isBlank() ? "N/A" : value.strip();
        return text.replace('\r', ' ').replace('\n', ' ').substring(0, Math.min(text.length(), 100));
    }

    private static boolean hasOpenBlockingFinding(String review) {
        for (String line : review.split("\\R")) {
            if (!line.strip().startsWith("|")) {
                continue;
            }
            String[] columns = line.toLowerCase(Locale.ROOT).split("\\|", -1);
            if (columns.length > 8) {
                String severity = columns[2].strip();
                String resolution = columns[8].strip();
                boolean blocking = "critical".equals(severity) || "important".equals(severity);
                boolean resolved = resolution.startsWith("resolved")
                        || resolution.startsWith("verified") || resolution.startsWith("closed");
                if (blocking && !resolved) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Current uniquely selected plan task. */
    public record PlanCursor(String taskId, String status, boolean complete, int totalTasks) {
    }

    /** Approved unit-test verification phases. */
    public enum TestPhase {
        RED,
        GREEN,
        REFACTOR
    }

    /** Immutable exact-selector contract extracted from one approved plan task. */
    public record TestSelectorContract(List<String> red, List<String> green,
                                       List<String> refactor) {
        /** Freeze every selector list. */
        public TestSelectorContract {
            red = List.copyOf(red);
            green = List.copyOf(green);
            refactor = List.copyOf(refactor);
        }

        private List<String> forPhase(TestPhase phase) {
            LinkedHashSet<String> selectors = new LinkedHashSet<>(red);
            if (phase == TestPhase.GREEN || phase == TestPhase.REFACTOR) {
                selectors.addAll(green);
            }
            if (phase == TestPhase.REFACTOR) {
                selectors.addAll(refactor);
            }
            return List.copyOf(selectors);
        }

        private boolean hasAny() {
            return !red.isEmpty() || !green.isEmpty() || !refactor.isEmpty();
        }
    }

    /** Supported independent review verdicts. */
    public enum Verdict {
        PASS,
        REWORK
    }

    private record Task(String id, String status, TestSelectorContract testSelectors) {
    }

    private record Heading(String id, int start) {
    }

    private enum PlanField {
        NONE,
        RED,
        GREEN,
        REFACTOR;

        private static PlanField from(String value) {
            return switch (value.strip().toUpperCase(Locale.ROOT)) {
                case "RED" -> RED;
                case "GREEN" -> GREEN;
                case "REFACTOR" -> REFACTOR;
                default -> NONE;
            };
        }
    }
}
