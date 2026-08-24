/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.CommitScope;
import com.openjiuwen.auto_harness.infra.EditScope;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.rails.EditSafetyRail;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitFacts;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Create a git commit for the current task.
 *
 * <p>Mirrors Python's {@code CommitStage} in
 * {@code openjiuwen/auto_harness/stages/commit.py}.</p>
 */
public class CommitStage extends TaskStage {

    private static final String VERIFY_REPORT = "verify_report";
    private static final String COMMIT_RESULT = "commit_result";
    private static final String TASK_RESULT = "task_result";

    @Override
    public String name() {
        return "commit";
    }

    @Override
    public String slot() {
        return "commit";
    }

    @Override
    public String displayName() {
        return "提交变更";
    }

    @Override
    public String description() {
        return "Create a git commit for the task.";
    }

    @Override
    public List<String> consumes() {
        return List.of(VERIFY_REPORT);
    }

    @Override
    public List<String> produces() {
        return List.of(COMMIT_RESULT);
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof TaskContext taskContext)) {
            throw new IllegalArgumentException("CommitStage requires TaskContext");
        }
        VerifyReportArtifact verifyReport = requireVerifyReport(taskContext.requireArtifact(VERIFY_REPORT));
        if (verifyReport.isReverted()) {
            return List.of((Object) StageResult.builder()
                    .status("failed")
                    .error(nullToEmpty(verifyReport.getError()))
                    .build()).iterator();
        }

        List<Object> events = new ArrayList<>();
        OptimizationTask task = taskContext.getTask();
        GitOperations git = taskContext.getOrchestrator().getGit();
        TaskRuntime runtime = taskContext.getRuntime();
        CommitFacts facts = collectCommitFacts(
                task,
                git,
                editSafetyRail(runtime),
                runtime.getPreexistingDirtyFiles(),
                verifyReport.getCiResult(),
                verifyReport.getFixErrors()
        );
        List<String> messages = new ArrayList<>(List.of("检查提交范围", "提交变更"));

        CommitRoundStreamResult round = runCommitRoundStream(
                commitAgent(runtime),
                task,
                git,
                facts,
                "",
                "",
                ""
        );
        events.addAll(round.events());
        CommitRoundResult roundResult = round.result();
        boolean commitOk = roundResult.ok();
        String reason = roundResult.reason();
        String statusText = roundResult.statusText();
        String lastCommitStat = roundResult.lastCommitStat();

        if (!commitOk) {
            messages.add("首次提交未成功:\n" + formatCommitFailure(
                    reason,
                    statusText,
                    lastCommitStat
            ));
            CommitFacts refreshedFacts = collectCommitFacts(
                    task,
                    git,
                    editSafetyRail(runtime),
                    runtime.getPreexistingDirtyFiles(),
                    verifyReport.getCiResult(),
                    verifyReport.getFixErrors()
            );
            round = runCommitRoundStream(
                    commitAgent(runtime),
                    task,
                    git,
                    refreshedFacts,
                    reason,
                    statusText,
                    lastCommitStat
            );
            events.addAll(round.events());
            roundResult = round.result();
            commitOk = roundResult.ok();
            reason = roundResult.reason();
            statusText = roundResult.statusText();
            lastCommitStat = roundResult.lastCommitStat();
            facts = refreshedFacts;
        }

        if (!commitOk) {
            String formattedError = formatCommitFailure(reason, statusText, lastCommitStat);
            if (task != null) {
                task.setStatus(TaskStatus.FAILED);
            }
            taskContext.getOrchestrator().getExperienceStore().record(Experience.builder()
                    .type(ExperienceType.FAILURE)
                    .topic(task == null ? "" : nullToEmpty(task.getTopic()))
                    .summary("commit failed")
                    .outcome("failed")
                    .details(formattedError)
                    .filesChanged(new ArrayList<>(facts.getAllowedFiles()))
                    .build()).join();
            CycleResult result = CycleResult.builder()
                    .success(false)
                    .error(formattedError)
                    .build();
            Map<String, Object> artifacts = new LinkedHashMap<>();
            artifacts.put(COMMIT_RESULT, commitArtifact(facts, statusText, lastCommitStat, false, formattedError));
            artifacts.put(TASK_RESULT, result);
            List<String> failedMessages = new ArrayList<>(messages);
            failedMessages.add("提交失败: " + formattedError);
            events.add(StageResult.builder()
                    .status("failed")
                    .artifacts(artifacts)
                    .messages(failedMessages)
                    .error(formattedError)
                    .build());
            return events.iterator();
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put(COMMIT_RESULT, commitArtifact(facts, statusText, lastCommitStat, true, ""));
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(messages)
                .build());
        return events.iterator();
    }

    /**
     * Mirrors Python's {@code _derive_allowed_files} in
     * {@code openjiuwen/auto_harness/stages/commit.py}.
     */
    public static List<String> deriveAllowedFiles(CommitFacts facts) {
        Set<String> editedSet = new LinkedHashSet<>(listOrEmpty(facts.getEditedFiles()));
        if (editedSet.isEmpty()) {
            return List.of();
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String path : listOrEmpty(facts.getTaskDeclaredFiles())) {
            if (!editedSet.contains(path) || !EditScope.isAllowedRepoEditPath(path)) {
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
        for (String path : listOrEmpty(facts.getDerivedTestFiles())) {
            if (!editedSet.contains(path) || !EditScope.isAllowedRepoEditPath(path)) {
                continue;
            }
            if (CommitScope.isDerivedTestFile(listOrEmpty(facts.getTaskDeclaredFiles()), path)) {
                allowed.add(path);
            }
        }
        for (String path : listOrEmpty(facts.getLegacyRelatedTestFiles())) {
            if (editedSet.contains(path) && EditScope.isAllowedRepoEditPath(path)) {
                allowed.add(path);
            }
        }
        if (listOrEmpty(facts.getTaskDeclaredFiles()).isEmpty()) {
            allowed.clear();
            for (String path : editedSet) {
                if (!EditScope.isAllowedRepoEditPath(path)) {
                    continue;
                }
                if (!CommitScope.isDocumentationFile(path)) {
                    allowed.add(path);
                    continue;
                }
                if (CommitScope.isAllowedDocumentationFile(path)) {
                    allowed.add(path);
                }
            }
        }
        List<String> result = new ArrayList<>(allowed);
        result.sort(String::compareTo);
        return result;
    }

    /**
     * Mirrors Python's {@code _build_commit_prompt} in
     * {@code openjiuwen/auto_harness/stages/commit.py}.
     */
    public static String buildCommitPrompt(
            OptimizationTask task,
            CommitFacts facts,
            String retryReason,
            String retryStatus,
            String lastCommitStat
    ) {
        String retryText = isBlank(retryReason)
                ? ""
                : "\n上一次提交尝试失败原因:\n" + retryReason + "\n";
        String statusText = isBlank(retryReason)
                ? ""
                : "\n上一次提交尝试后的 git status --porcelain:\n"
                + (isBlank(retryStatus) ? "无" : retryStatus) + "\n";
        String commitStatText = isBlank(lastCommitStat)
                ? ""
                : "\n最近一次提交摘要:\n" + lastCommitStat + "\n";
        return "任务: " + nullToEmpty(task == null ? "" : task.getTopic()) + "\n"
                + "描述: " + nullToEmpty(task == null ? "" : task.getDescription()) + "\n"
                + "声明文件: " + joinOrNone(facts.getTaskDeclaredFiles()) + "\n"
                + "当前脏文件: " + joinOrNone(facts.getCurrentDirtyFiles()) + "\n"
                + "本轮实际修改: " + joinOrNone(facts.getEditedFiles()) + "\n"
                + "允许提交文件: " + joinOrNone(facts.getAllowedFiles()) + "\n"
                + "派生测试文件: " + joinOrNone(facts.getDerivedTestFiles()) + "\n"
                + "验证关联老测试: " + joinOrNone(facts.getLegacyRelatedTestFiles()) + "\n"
                + "禁止混入旧脏文件: " + joinOrNone(facts.getPreexistingDirtyFiles()) + "\n"
                + "diff 统计:\n" + (isBlank(facts.getDiffStat()) ? "无" : facts.getDiffStat()) + "\n"
                + retryText
                + statusText
                + commitStatText + "\n"
                + "请遵循 commit skill，通过 bash 执行 git status、git add 明确文件路径、git commit，并在提交后自检。";
    }

    /**
     * Mirrors Python's {@code _collect_commit_facts} in
     * {@code openjiuwen/auto_harness/stages/commit.py}.
     */
    public static CommitFacts collectCommitFacts(
            OptimizationTask task,
            GitOperations git,
            EditSafetyRail editSafetyRail,
            List<String> preexistingDirtyFiles,
            Map<String, Object> ciResult,
            String fixErrors
    ) {
        try {
            Map<String, List<String>> status = git.collectStatus();
            List<String> dirtyFiles = new ArrayList<>(status.getOrDefault("dirty_files", List.of()));
            List<String> editedFiles = editSafetyRail == null
                    ? new ArrayList<>()
                    : new ArrayList<>(editSafetyRail.editedFiles());
            if (editedFiles.isEmpty()) {
                Set<String> preSet = new LinkedHashSet<>(listOrEmpty(preexistingDirtyFiles));
                for (String file : dirtyFiles) {
                    if (!preSet.contains(file)) {
                        editedFiles.add(file);
                    }
                }
            }

            List<String> taskFiles = task == null ? List.of() : listOrEmpty(task.getFiles());
            List<String> verifyRelatedFiles = CommitScope.extractVerifyRelatedFiles(ciResult, fixErrors);
            List<String> derivedTestFiles = new ArrayList<>();
            for (String path : editedFiles) {
                if (!dirtyFiles.contains(path)) {
                    continue;
                }
                if (CommitScope.isDerivedTestFile(taskFiles, path)) {
                    derivedTestFiles.add(path);
                }
            }
            List<String> legacyRelatedTestFiles = CommitScope.deriveLegacyRelatedTestFiles(
                    editedFiles,
                    verifyRelatedFiles
            );
            CommitFacts facts = CommitFacts.builder()
                    .branchName(git.currentBranch())
                    .taskDeclaredFiles(new ArrayList<>(taskFiles))
                    .preexistingDirtyFiles(new ArrayList<>(listOrEmpty(preexistingDirtyFiles)))
                    .currentDirtyFiles(dirtyFiles)
                    .trackedModifiedFiles(new ArrayList<>(status.getOrDefault("tracked_modified_files", List.of())))
                    .untrackedFiles(new ArrayList<>(status.getOrDefault("untracked_files", List.of())))
                    .editedFiles(editedFiles)
                    .derivedTestFiles(derivedTestFiles)
                    .legacyRelatedTestFiles(legacyRelatedTestFiles)
                    .verifyRelatedFiles(verifyRelatedFiles)
                    .diffStat(git.diffStat(null))
                    .build();
            facts.setAllowedFiles(deriveAllowedFiles(facts));
            return facts;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to collect commit facts", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while collecting commit facts", e);
        }
    }

    private static CommitRoundStreamResult runCommitRoundStream(
            DeepAgent commitAgent,
            OptimizationTask task,
            GitOperations git,
            CommitFacts facts,
            String retryReason,
            String retryStatus,
            String lastCommitStat
    ) {
        if (commitAgent == null) {
            return new CommitRoundStreamResult(List.of(), new CommitRoundResult(
                    false,
                    "No agent available for commit phase.",
                    "",
                    ""
            ));
        }
        try {
            String beforeHead = git.currentHead();
            List<Object> events = new ArrayList<>();
            Iterator<?> iterator = commitAgent.stream(Map.of(
                    "query",
                    buildCommitPrompt(task, facts, retryReason, retryStatus, lastCommitStat)
            ));
            while (iterator.hasNext()) {
                events.add(iterator.next());
            }
            String afterHead = git.currentHead();
            String statusText = git.statusPorcelain();
            String latestCommit = "";
            if (!afterHead.equals(beforeHead)) {
                latestCommit = git.showLastCommitStat();
                return new CommitRoundStreamResult(
                        events,
                        new CommitRoundResult(true, "", statusText, latestCommit)
                );
            }
            return new CommitRoundStreamResult(
                    events,
                    new CommitRoundResult(
                            false,
                            "Agent did not create a git commit during commit phase.",
                            statusText,
                            latestCommit
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run commit round", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running commit round", e);
        }
    }

    private static String formatCommitFailure(String reason, String statusText, String lastCommitStat) {
        List<String> details = new ArrayList<>();
        details.add(nullToEmpty(reason));
        if (!isBlank(statusText)) {
            details.add("当前 git status --porcelain:\n" + statusText);
        }
        if (!isBlank(lastCommitStat)) {
            details.add("最近一次提交摘要:\n" + lastCommitStat);
        }
        return String.join("\n", details);
    }

    private static CommitArtifact commitArtifact(
            CommitFacts facts,
            String statusText,
            String lastCommitStat,
            boolean committed,
            String error
    ) {
        return CommitArtifact.builder()
                .facts(facts)
                .statusText(nullToEmpty(statusText))
                .lastCommitStat(nullToEmpty(lastCommitStat))
                .branchName(nullToEmpty(facts.getBranchName()))
                .committed(committed)
                .error(nullToEmpty(error))
                .build();
    }

    private static VerifyReportArtifact requireVerifyReport(Object artifact) {
        if (artifact instanceof VerifyReportArtifact verifyReport) {
            return verifyReport;
        }
        throw new IllegalArgumentException("verify_report must be VerifyReportArtifact");
    }

    private static EditSafetyRail editSafetyRail(TaskRuntime runtime) {
        return runtime != null && runtime.getEditSafetyRail() instanceof EditSafetyRail rail ? rail : null;
    }

    private static DeepAgent commitAgent(TaskRuntime runtime) {
        if (runtime == null) {
            return null;
        }
        if (runtime.getCommitAgent() instanceof DeepAgent agent) {
            return agent;
        }
        return runtime.getTaskAgent() instanceof DeepAgent agent ? agent : null;
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "无" : String.join(", ", values);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Mirrors Python's {@code CommitRoundResult} in
     * {@code openjiuwen/auto_harness/stages/commit.py}.
     */
    public record CommitRoundResult(boolean ok, String reason, String statusText, String lastCommitStat) {
    }

    private record CommitRoundStreamResult(List<Object> events, CommitRoundResult result) {
    }
}
