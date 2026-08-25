/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import com.openjiuwen.core.foundation.tool.Tool;
import examples.gitcode_issue_evolver.agent.CodeCheckRepairDirective;
import examples.gitcode_issue_evolver.agent.IssueApprovedGateWorkflow;
import examples.gitcode_issue_evolver.agent.IssueWorkerAgent;
import examples.gitcode_issue_evolver.agent.RestrictedFileTools;
import examples.gitcode_issue_evolver.agent.TrustedSkillTools;
import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;
import examples.gitcode_issue_evolver.job.EvolutionJob;
import examples.gitcode_issue_evolver.job.EvolutionJobState;
import examples.gitcode_issue_evolver.job.IssueFailureCategory;
import examples.gitcode_issue_evolver.job.IssueJobRequest;
import examples.gitcode_issue_evolver.job.SqliteEvolutionJobStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic checks for the Issue Controller contract and bounded file tools. */
public final class IssueControllerDeterministicTest {
    private IssueControllerDeterministicTest() {
    }

    /** Run deterministic Controller checks. */
    public static void main(String[] args) throws Exception {
        testApprovedGateSchema();
        testBoundedToolsAndSkippedFiles();
        testTrustedSkillTools();
        testCodeCheckTargetedRepairContract();
        testCodeCheckStandardOnlyOverride();
        testResourceTargetsAreExcluded();
        testFailureStateClassification();
        testSchemaV5AuditPersistence();
        System.out.println("IssueControllerDeterministicTest: PASS");
    }

    private static void testApprovedGateSchema() {
        Map<String, Object> schema = IssueApprovedGateWorkflow.inputSchema();
        require(Boolean.FALSE.equals(schema.get("additionalProperties")),
                "runApprovedGate accepted model-controlled arguments");
        require(schema.get("properties") instanceof Map<?, ?> properties
                        && properties.isEmpty(),
                "runApprovedGate exposed Controller-owned parameters");
    }

    private static void testBoundedToolsAndSkippedFiles() throws Exception {
        Path worktree = Files.createTempDirectory("issue-controller-tools-");
        Path source = worktree.resolve("src/main/java/example/Large.java");
        Files.createDirectories(source.getParent());
        List<String> lines = new ArrayList<>();
        for (int index = 1; index <= 1_000; index++) {
            lines.add("line-" + index + " " + "x".repeat(600));
        }
        Files.write(source, lines, StandardCharsets.UTF_8);
        Files.write(source.getParent().resolve("Binary.dat"),
                new byte[]{(byte) 0xC3, (byte) 0x28});
        Files.writeString(source.getParent().resolve("Searchable.txt"),
                "unique-repair-marker\n", StandardCharsets.UTF_8);

        List<Tool> tools = new RestrictedFileTools(worktree, "issue-tools").create();
        Map<?, ?> first = result(tool(tools, "readFile").invoke(
                Map.of("path", "src/main/java/example/Large.java", "limit", 1_000), Map.of()));
        require(Boolean.TRUE.equals(first.get("hasMore"))
                        && Boolean.TRUE.equals(first.get("outputCapped"))
                        && String.valueOf(first.get("content")).length() <= 40_000,
                "readFile did not bound a large model-visible result");
        int nextOffset = ((Number) first.get("nextOffset")).intValue();
        Map<?, ?> second = result(tool(tools, "readFile").invoke(
                Map.of("path", "src/main/java/example/Large.java",
                        "offset", nextOffset, "limit", 10), Map.of()));
        require(String.valueOf(second.get("content")).startsWith("line-" + nextOffset),
                "readFile did not resume at its stable continuation offset");

        Map<?, ?> search = result(tool(tools, "searchFiles").invoke(
                Map.of("path", "src/main/java/example", "query", "unique-repair-marker"), Map.of()));
        require(!String.valueOf(search.get("matches")).isBlank()
                        && ((Number) search.get("skippedUnreadableFiles")).intValue() == 1
                        && Boolean.FALSE.equals(search.get("scanComplete")),
                "one unreadable or non-UTF-8 file aborted the repository search");
        Map<?, ?> largeSearch = result(tool(tools, "searchFiles").invoke(
                Map.of("path", "src/main/java/example", "query", "line-"), Map.of()));
        require(Boolean.TRUE.equals(largeSearch.get("outputCapped"))
                        && String.valueOf(largeSearch.get("matches")).length() <= 45_000,
                "searchFiles did not bound a large model-visible result");

        tool(tools, "replaceInFile").invoke(Map.of(
                "path", "src/main/java/example/Large.java",
                "oldContent", "line-1 " + "x".repeat(600),
                "newContent", "line-1 repaired"), Map.of());
        require(Files.readString(source).startsWith("line-1 repaired"),
                "replaceInFile did not apply one exact bounded change");

        Path resourceDirectory = worktree.resolve("src/main/resources");
        Files.createDirectories(resourceDirectory);
        tool(tools, "writeFile").invoke(Map.of(
                "path", "src/main/resources/application.yaml", "content", "enabled: true\n"), Map.of());
        require(Files.readString(resourceDirectory.resolve("application.yaml")).contains("enabled: true"),
                "src/main resources were rejected by the source/test write scope");
    }

    private static void testFailureStateClassification() {
        require(AutoEvolvingWorker.failureState(
                        IssueExecutionErrorCode.NO_ACTION_REQUIRED, false)
                        == EvolutionJobState.NO_ACTION_REQUIRED,
                "NO_ACTION was collapsed into a generic failure");
        require(AutoEvolvingWorker.failureState(
                        IssueExecutionErrorCode.COMMIT_VALIDATION_FAILED, false)
                        == EvolutionJobState.FAILED_POLICY,
                "policy failure was collapsed into an automation failure");
        require(AutoEvolvingWorker.failureState(
                        IssueExecutionErrorCode.AGENT_FAILED_TO_ACT, false)
                        == EvolutionJobState.FAILED_AUTOMATION,
                "Agent no-diff exhaustion was not classified as automation failure");
    }

    private static void testTrustedSkillTools() throws Exception {
        Path root = Files.createTempDirectory("issue-trusted-skills-");
        Path rule = root.resolve("coding-standard-full/rules/G.OTH.md");
        Files.createDirectories(rule.getParent());
        Files.writeString(root.resolve("coding-standard-full/SKILL.md"),
                "# Complete coding standard\n", StandardCharsets.UTF_8);
        Files.writeString(rule, "# G.OTH\nG.OTH.01 use secure random\n", StandardCharsets.UTF_8);
        List<Tool> tools = new TrustedSkillTools(root, "issue-skill-tools").create();
        Map<?, ?> read = result(tool(tools, "readSkillFile").invoke(Map.of(
                "path", "coding-standard-full/rules/G.OTH.md"), Map.of()));
        require(String.valueOf(read.get("content")).contains("G.OTH.01"),
                "trusted coding-standard category was not readable by the worker");
        Map<?, ?> search = result(tool(tools, "searchSkillFiles").invoke(Map.of(
                "query", "secure random", "path", "coding-standard-full"), Map.of()));
        require(String.valueOf(search.get("matches")).contains("G.OTH.md:2"),
                "trusted Skill search did not return the authoritative rule location");
        boolean traversalRejected = false;
        try {
            tool(tools, "readSkillFile").invoke(Map.of("path", "../secret"), Map.of());
        } catch (RuntimeException ex) {
            traversalRejected = true;
        }
        require(traversalRejected, "trusted Skill tool allowed path traversal");
    }

    private static void testCodeCheckTargetedRepairContract() {
        GitCodeIssue codeCheck = new GitCodeIssue(90L, "Fix G.OTH.01", """
                CodeCheck reports G.OTH.01 at
                src/main/java/com/openjiuwen/example/TokenFactory.java:64.
                Also inspect src/test/java/com/openjiuwen/example/TokenFactoryTest.java#L31.
                """, "open", "https://gitcode/issues/90", List.of(), List.of("bug/codecheck"));
        CodeCheckRepairDirective directive = CodeCheckRepairDirective.from(codeCheck);
        require(directive.isCodeCheck(), "bug/codecheck did not select the targeted repair path");
        require(directive.ruleIds().equals(List.of("G.OTH.01"))
                        && directive.categories().equals(List.of("G.OTH")),
                "CodeCheck rule/category extraction was not deterministic");
        require(directive.locations().size() == 2
                        && Long.valueOf(64L).equals(directive.locations().get(0).line())
                        && Long.valueOf(31L).equals(directive.locations().get(1).line()),
                "CodeCheck file and line extraction lost reported targets");
        String prompt = directive.promptSection();
        require(prompt.contains("TARGETED_STANDARD_REPAIR")
                        && prompt.contains("required_standard_categories_first: G.OTH")
                        && prompt.contains("Do not perform broad repository analysis"),
                "CodeCheck Controller envelope did not enforce direct standard repair");

        IssueWorkerAgent.Result noAction = new IssueWorkerAgent.Result(
                IssueWorkerAgent.Status.NO_ACTION, "false positive", "{}",
                "NO_ACTION_CONFIRMED", "scanner finding is low risk");
        require(!ExampleIssueTaskExecutor.acceptsNoAction(codeCheck, noAction),
                "CodeCheck NO_ACTION bypassed the repair loop");
        GitCodeIssue ordinary = new GitCodeIssue(91L, "Ordinary bug", "Description",
                "open", "https://gitcode/issues/91", List.of(), List.of("bug"));
        require(!CodeCheckRepairDirective.from(ordinary).isCodeCheck()
                        && ExampleIssueTaskExecutor.acceptsNoAction(ordinary, noAction),
                "ordinary evidence-backed NO_ACTION compatibility was lost");
    }

    private static void testResourceTargetsAreExcluded() {
        GitCodeIssue topLevel = new GitCodeIssue(97L, "CodeCheck resource script", """
                修复 resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py:203
                """, "open", "https://gitcode/issues/97", List.of(), List.of("bug/codecheck"));
        SparseCheckoutIssuePolicy.Validation topLevelResult = SparseCheckoutIssuePolicy.validate(topLevel);
        require(!topLevelResult.allowed()
                        && topLevelResult.excludedPaths().contains(
                                "resources/skills/gitcode-submit-issue/scripts/gitcode_issue.py"),
                "top-level resources target was admitted to the Java Issue worker");

        GitCodeIssue javaResource = new GitCodeIssue(100L, "Resource change", """
                Update src/main/resources/application.yaml for this issue.
                """, "open", "https://gitcode/issues/100", List.of(), List.of("bug/codecheck"));
        require(SparseCheckoutIssuePolicy.validate(javaResource).allowed(),
                "src/main/resources was incorrectly treated as root resources scope");
    }

    private static void testCodeCheckStandardOnlyOverride() {
        GitCodeIssue codeCheck = new GitCodeIssue(99L, "Fix G.SER.01", """
                ## 问题描述
                G.SER.01 at src/main/java/com/openjiuwen/example/Record.java:42.

                ## 改进与修复建议
                Treat this finding as a false positive and request a product decision.

                ## 验收标准
                The reported coding-standard finding is removed.
                """, "open", "https://gitcode/issues/99", List.of(
                "PRODUCT_DECISION_REQUIRED; do not edit source. Corrected location: "
                        + "src/main/java/com/openjiuwen/core/example/Record.java:42"),
                List.of("bug/codecheck"));
        CodeCheckRepairDirective directive = CodeCheckRepairDirective.from(codeCheck);
        require(directive.locations().size() == 2
                        && directive.locations().get(1).path().contains("core/example"),
                "Controller did not retain corrected location evidence before comment isolation");
        String description = CodeCheckRepairDirective.descriptionForPrompt(codeCheck, true);
        require(description.contains("问题描述") && description.contains("验收标准")
                        && !description.contains("false positive")
                        && !description.contains("改进与修复建议"),
                "standard-only override did not remove the remediation section");
        require(CodeCheckRepairDirective.commentsForPrompt(codeCheck, true).isEmpty(),
                "standard-only override exposed comment remediation judgments to the Agent");
        require(directive.promptSection(true).contains("standard_only_override: ENABLED")
                        && directive.promptSection(true).contains("same file or construct"),
                "standard-only Controller policy omitted remediation or stale-path recovery");
    }

    private static void testSchemaV5AuditPersistence() throws Exception {
        Path database = Files.createTempDirectory("issue-controller-db-").resolve("jobs.db");
        String jobId;
        try (SqliteEvolutionJobStore store = new SqliteEvolutionJobStore(database)) {
            EvolutionJob job = store.enqueueIssue(new IssueJobRequest(
                    "delivery-controller", "issue_poll", "hash-controller",
                    "owner/repo", 77L, "Controller test",
                    "https://gitcode.com/owner/repo/issues/77", "auto/77-controller"))
                    .job().orElseThrow();
            jobId = job.id();
            store.recordFailureEvent(jobId, "BUGFIX", "VERIFICATION_FAILED",
                    IssueFailureCategory.AGENT_CORRECTABLE,
                    "Gate failed", "bounded diagnostic");
            store.recordGateReceipt(jobId, "fingerprint-1", "FAILED", "TARGETED",
                    "VERIFICATION_FAILED", "AGENT_CORRECTABLE", false, 1,
                    "bounded output", System.currentTimeMillis());
            require(store.findGateReceipt(jobId, "fingerprint-1").isPresent(),
                    "durable Gate receipt could not be reused by fingerprint");
            require(store.recentFailureContext(jobId, 8).stream()
                            .anyMatch(value -> value.contains("VERIFICATION_FAILED")),
                    "structured failure context could not be reconstructed");
            store.transition(jobId, job.version(), EvolutionJobState.FAILED_AUTOMATION,
                    "prepare legacy terminal state");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE evolution_jobs SET state='FAILED_FINAL' WHERE id=?")) {
            statement.setString(1, jobId);
            statement.executeUpdate();
        }
        try (SqliteEvolutionJobStore migrated = new SqliteEvolutionJobStore(database)) {
            require(migrated.findById(jobId).orElseThrow().state()
                            == EvolutionJobState.FAILED_AUTOMATION,
                    "schema v5 did not migrate legacy FAILED_FINAL state");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            require(singleCount(statement, "issue_failure_events") == 1,
                    "schema v5 did not retain the structured failure event");
            require(singleCount(statement, "issue_gate_receipts") == 1,
                    "schema v5 did not retain the Approved Gate receipt");
        }
    }

    private static int singleCount(java.sql.Statement statement, String table) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static Tool tool(List<Tool> tools, String name) {
        return tools.stream().filter(tool -> name.equals(tool.getCard().getName()))
                .findFirst().orElseThrow();
    }

    private static Map<?, ?> result(Object value) {
        if (!(value instanceof Map<?, ?> result)) {
            throw new AssertionError("Tool result was not a map");
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
