/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.TaskContext;
import com.openjiuwen.autoharness.infra.CommitScope;
import com.openjiuwen.autoharness.schema.CommitArtifact;
import com.openjiuwen.autoharness.schema.CommitFacts;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.schema.TaskStatus;
import com.openjiuwen.autoharness.schema.VerifyReportArtifact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class CommitStage used by the Java parity implementation.
 *
 * @since 1.0
 */
public class CommitStage extends TaskStage {
    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "commit";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Create a git commit for the task.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> consumes() {
        return List.of("verify_report");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> produces() {
        return List.of("commit_result");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public StageResult run(BaseExecutionContext ctx) {
        for (Object event : stream(ctx)) {
            if (event instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder()
                .status("failed")
                .error("commit stage did not return StageResult")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            return List.of(failed("commit requires TaskContext", null, null, "", ""));
        }
        Object verifyRaw = taskContext.requireArtifact("verify_report");
        VerifyReportArtifact verifyReport = verifyRaw instanceof VerifyReportArtifact report
                ? report
                : VerifyReportArtifact.builder().build();
        if (verifyReport.isReverted()) {
            return List.of(StageResult.builder()
                    .status("failed")
                    .error(verifyReport.getError())
                    .build());
        }
        CommitFacts facts = collectCommitFacts(taskContext, verifyReport);
        List<String> messages = new ArrayList<>(List.of("[4/5] 检查提交范围", "[5/5] 提交变更"));
        CommitRoundResult firstRound = runCommitRoundStream(
                taskContext.getRuntime().getCommitAgent() != null
                        ? taskContext.getRuntime().getCommitAgent()
                        : taskContext.getRuntime().getTaskAgent(),
                taskContext.getTask(),
                taskContext.getOrchestrator().getGit(),
                facts,
                "",
                "",
                ""
        );
        List<Object> events = new ArrayList<>(firstRound.events());
        CommitRoundResult finalRound = firstRound;
        if (!firstRound.isOk()) {
            messages.add("首次提交未成功:\n" + formatCommitFailure(
                    firstRound.reason(),
                    firstRound.statusText(),
                    firstRound.lastCommitStat()
            ));
            CommitFacts refreshedFacts = collectCommitFacts(taskContext, verifyReport);
            CommitRoundResult retryRound = runCommitRoundStream(
                    taskContext.getRuntime().getCommitAgent() != null
                            ? taskContext.getRuntime().getCommitAgent()
                            : taskContext.getRuntime().getTaskAgent(),
                    taskContext.getTask(),
                    taskContext.getOrchestrator().getGit(),
                    refreshedFacts,
                    firstRound.reason(),
                    firstRound.statusText(),
                    firstRound.lastCommitStat()
            );
            events.addAll(retryRound.events());
            finalRound = retryRound;
            facts = refreshedFacts;
        }
        StageResult result = finalRound.isOk()
                ? success(facts, finalRound.statusText(), finalRound.lastCommitStat(), messages)
                : failed(
                        formatCommitFailure(finalRound.reason(), finalRound.statusText(), finalRound.lastCommitStat()),
                        facts,
                        taskContext,
                        finalRound.statusText(),
                        finalRound.lastCommitStat(),
                        messages
                );
        if (!finalRound.isOk()) {
            recordCommitFailure(taskContext, facts, result.getError());
        }
        events.add(result);
        return events;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static CommitFacts collectCommitFacts(OptimizationTask task, Map<String, List<String>> status,
                                                 List<String> editedFiles,
                                                 List<String> preexistingDirtyFiles,
                                                 VerifyReportArtifact verifyReport) {
        List<String> dirtyFiles = getList(status, "dirty_files");
        List<String> derivedTestFiles = new ArrayList<>();
        for (String path : editedFiles == null ? List.<String>of() : editedFiles) {
            if (dirtyFiles.contains(path) && CommitScope.isDerivedTestFile(taskFiles(task), path)) {
                derivedTestFiles.add(path);
            }
        }
        List<String> verifyRelated = CommitScope.extractVerifyRelatedFiles(
                ciResultToGateResult(verifyReport),
                verifyReport == null ? "" : verifyReport.getFixErrors()
        );
        List<String> legacyRelated = CommitScope.deriveLegacyRelatedTestFiles(
                editedFiles == null ? List.of() : editedFiles,
                verifyRelated
        );
        CommitFacts facts = CommitFacts.builder()
                .branchName("")
                .taskDeclaredFiles(taskFiles(task))
                .preexistingDirtyFiles(preexistingDirtyFiles == null ? List.of() : preexistingDirtyFiles)
                .currentDirtyFiles(dirtyFiles)
                .trackedModifiedFiles(getList(status, "tracked_modified_files"))
                .untrackedFiles(getList(status, "untracked_files"))
                .editedFiles(editedFiles == null ? List.of() : editedFiles)
                .derivedTestFiles(derivedTestFiles)
                .legacyRelatedTestFiles(legacyRelated)
                .verifyRelatedFiles(verifyRelated)
                .build();
        facts.setAllowedFiles(deriveAllowedFiles(facts));
        return facts;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static CommitFacts collectCommitFacts(TaskContext ctx, VerifyReportArtifact verifyReport) {
        CommitFacts facts = collectCommitFacts(
                ctx.getTask(),
                ctx.getOrchestrator().getGit().collectStatus(),
                editedFiles(ctx),
                ctx.getRuntime().getPreexistingDirtyFiles(),
                verifyReport
        );
        facts.setBranchName(ctx.getOrchestrator().getGit().currentBranch());
        facts.setDiffStat(ctx.getOrchestrator().getGit().diffStat());
        return facts;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<String> deriveAllowedFiles(CommitFacts facts) {
        Set<String> edited = new LinkedHashSet<>(facts.getEditedFiles());
        if (edited.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String path : facts.getTaskDeclaredFiles()) {
            if (!edited.contains(path) || !CommitScope.isAllowedRepoEditPath(path)) {
                continue;
            }
            if (CommitScope.isDocumentationFile(path)) {
                if (CommitScope.isAllowedDocumentationFile(path)) {
                    allowed.add(path);
                }
                continue;
            }
            allowed.add(path);
        }
        for (String path : facts.getDerivedTestFiles()) {
            if (edited.contains(path)
                    && CommitScope.isAllowedRepoEditPath(path)
                    && CommitScope.isDerivedTestFile(facts.getTaskDeclaredFiles(), path)) {
                allowed.add(path);
            }
        }
        for (String path : facts.getLegacyRelatedTestFiles()) {
            if (edited.contains(path) && CommitScope.isAllowedRepoEditPath(path)) {
                allowed.add(path);
            }
        }
        if (facts.getTaskDeclaredFiles().isEmpty()) {
            allowed.clear();
            for (String path : edited) {
                if (!CommitScope.isAllowedRepoEditPath(path)) {
                    continue;
                }
                if (!CommitScope.isDocumentationFile(path) || CommitScope.isAllowedDocumentationFile(path)) {
                    allowed.add(path);
                }
            }
        }
        return new ArrayList<>(allowed).stream().sorted().toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String buildCommitPrompt(OptimizationTask task, CommitFacts facts,
                                           String retryReason, String retryStatus, String lastCommitStat) {
        String retryText = hasText(retryReason) ? "\n上一次提交尝试失败原因:\n" + retryReason + "\n" : "";
        String statusText = hasText(retryReason)
                ? "\n上一次提交尝试后的 git status --porcelain:\n" + valueOrDefault(retryStatus, "无") + "\n"
                : "";
        String commitStatText = hasText(lastCommitStat) ? "\n最近一次提交摘要:\n" + lastCommitStat + "\n" : "";
        return "任务: " + TaskContext.taskKey(task) + "\n"
                + "描述: " + value(task == null ? "" : task.getDescription()) + "\n"
                + "声明文件: " + joinOrNone(facts.getTaskDeclaredFiles()) + "\n"
                + "当前脏文件: " + joinOrNone(facts.getCurrentDirtyFiles()) + "\n"
                + "本轮实际修改: " + joinOrNone(facts.getEditedFiles()) + "\n"
                + "允许提交文件: " + joinOrNone(facts.getAllowedFiles()) + "\n"
                + "派生测试文件: " + joinOrNone(facts.getDerivedTestFiles()) + "\n"
                + "验证关联老测试: " + joinOrNone(facts.getLegacyRelatedTestFiles()) + "\n"
                + "禁止混入旧脏文件: " + joinOrNone(facts.getPreexistingDirtyFiles()) + "\n"
                + "diff 统计:\n" + valueOrDefault(facts.getDiffStat(), "无") + "\n"
                + retryText
                + statusText
                + commitStatText + "\n"
                + "请遵循 commit skill，通过 bash 执行 git status、git add 明确文件路径、git commit，并在提交后自检。";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String formatCommitFailure(String reason, String statusText, String lastCommitStat) {
        List<String> details = new ArrayList<>();
        details.add(value(reason));
        if (hasText(statusText)) {
            details.add("当前 git status --porcelain:\n" + statusText);
        }
        if (hasText(lastCommitStat)) {
            details.add("最近一次提交摘要:\n" + lastCommitStat);
        }
        return String.join("\n", details);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static CommitRoundResult runCommitRoundStream(Object commitAgent,
                                                         OptimizationTask task,
                                                         com.openjiuwen.autoharness.infra.GitOperations git,
                                                         CommitFacts facts,
                                                         String retryReason,
                                                         String retryStatus,
                                                         String lastCommitStat) {
        if (commitAgent == null) {
            return CommitRoundResult.builder()
                    .isOk(false)
                    .reason("No agent available for commit phase.")
                    .build();
        }
        String beforeHead = git.currentHead();
        List<Object> events = streamAgent(commitAgent, buildCommitPrompt(
                task,
                facts,
                retryReason,
                retryStatus,
                lastCommitStat
        ));
        String afterHead = git.currentHead();
        String statusText = git.statusPorcelain();
        String latestCommit = "";
        if (!value(beforeHead).equals(value(afterHead))) {
            latestCommit = git.showLastCommitStat();
            return CommitRoundResult.builder()
                    .isOk(true)
                    .statusText(statusText)
                    .lastCommitStat(latestCommit)
                    .events(events)
                    .build();
        }
        return CommitRoundResult.builder()
                .isOk(false)
                .reason("Agent did not create a git commit during commit phase.")
                .statusText(statusText)
                .lastCommitStat(latestCommit)
                .events(events)
                .build();
    }

    private static StageResult success(CommitFacts facts, String statusText, String lastCommitStat,
                                       List<String> messages) {
        return StageResult.builder()
                .artifacts(Map.of("commit_result", CommitArtifact.builder()
                        .facts(facts)
                        .statusText(value(statusText))
                        .lastCommitStat(value(lastCommitStat))
                        .branchName(value(facts == null ? "" : facts.getBranchName()))
                        .isCommitted(true)
                        .build()))
                .messages(messages == null ? List.of("[4/5] 检查提交范围", "[5/5] 提交变更") : messages)
                .build();
    }

    private static StageResult failed(String reason, CommitFacts facts, TaskContext ctx, String statusText,
                                      String lastCommitStat) {
        return failed(reason, facts, ctx, statusText, lastCommitStat,
                List.of("[4/5] 检查提交范围", "[5/5] 提交变更"));
    }

    private static StageResult failed(String reason, CommitFacts facts, TaskContext ctx, String statusText,
                                      String lastCommitStat, List<String> messages) {
        String formatted = value(reason);
        if (ctx != null) {
            ctx.getTask().setStatus(TaskStatus.FAILED);
        }
        List<String> outputMessages = new ArrayList<>(messages == null
                ? List.of("[4/5] 检查提交范围", "[5/5] 提交变更")
                : messages);
        outputMessages.add("提交失败: " + formatted);
        return StageResult.builder()
                .status("failed")
                .artifacts(Map.of(
                        "commit_result", CommitArtifact.builder()
                                .facts(facts)
                                .statusText(value(statusText))
                                .lastCommitStat(value(lastCommitStat))
                                .branchName(value(facts == null ? "" : facts.getBranchName()))
                                .isCommitted(false)
                                .error(formatted)
                                .build(),
                        "task_result", CycleResult.builder()
                                .isSuccess(false)
                                .error(formatted)
                                .build()
                ))
                .messages(outputMessages)
                .error(formatted)
                .build();
    }

    private static List<String> editedFiles(TaskContext ctx) {
        Object rail = ctx.getRuntime().getEditSafetyRail();
        if (rail == null) {
            return List.of();
        }
        try {
            Object result = rail.getClass().getMethod("editedFiles").invoke(rail);
            if (result instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
        return List.of();
    }

    private static List<Object> streamAgent(Object agent, String prompt) {
        try {
            Object stream = agent.getClass()
                    .getMethod("stream", Map.class)
                    .invoke(agent, Map.of("query", prompt));
            if (stream instanceof Iterator<?> iterator) {
                List<Object> events = new ArrayList<>();
                while (iterator.hasNext()) {
                    events.add(iterator.next());
                }
                return events;
            }
            if (stream instanceof Iterable<?> iterable) {
                List<Object> events = new ArrayList<>();
                for (Object event : iterable) {
                    events.add(event);
                }
                return events;
            }
        } catch (ReflectiveOperationException ex) {
            return List.of();
        }
        return List.of();
    }

    private static void recordCommitFailure(TaskContext ctx, CommitFacts facts, String formattedError) {
        try {
            ctx.getOrchestrator().getExperienceStore().record(Experience.builder()
                    .type(ExperienceType.FAILURE)
                    .topic(TaskContext.taskKey(ctx.getTask()))
                    .summary("commit failed")
                    .outcome("failed")
                    .details(value(formattedError))
                    .filesChanged(facts == null ? List.of() : facts.getAllowedFiles())
                    .build());
        } catch (IOException ignored) {
            // Python records failure experience but does not let persistence failure isReplace the stage result.
        }
    }

    private static com.openjiuwen.autoharness.infra.CIGateResult ciResultToGateResult(VerifyReportArtifact report) {
        if (report == null || report.getCiResult() == null) {
            return null;
        }
        return com.openjiuwen.autoharness.infra.CIGateResult.builder()
                .errors(String.valueOf(report.getCiResult().getOrDefault("errors", "")))
                .gateOutputs(extractGateOutputs(report.getCiResult()))
                .build();
    }

    private static List<String> extractGateOutputs(Map<String, Object> ciResult) {
        Object outputs = ciResult.get("gate_outputs");
        if (outputs instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static List<String> getList(Map<String, List<String>> map, String key) {
        if (map == null || map.get(key) == null) {
            return List.of();
        }
        return map.get(key);
    }

    private static List<String> taskFiles(OptimizationTask task) {
        return task == null || task.getFiles() == null ? List.of() : task.getFiles();
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "无" : String.join(", ", values);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @lombok.AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class CommitRoundResult {
        @lombok.Builder.Default
        private boolean isOk = false;
        @lombok.Builder.Default
        private String reason = "";
        @lombok.Builder.Default
        private String statusText = "";
        @lombok.Builder.Default
        private String lastCommitStat = "";
        @lombok.Builder.Default
        private List<Object> events = new ArrayList<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean isOk() {
            return isOk;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String reason() {
            return reason;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String statusText() {
            return statusText;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public String lastCommitStat() {
            return lastCommitStat;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<Object> events() {
            return events == null ? List.of() : events;
        }
    }
}
