/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitFacts;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PullRequestArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.TaskStatus;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.stages.PublishPRStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publish PR stage parity tests.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.publish_pr} in
 * {@code openjiuwen/auto_harness/stages/publish_pr.py}.</p>
 */
class TestPublishPRStage {

    @TempDir
    private Path tempDir;

    @Test
    void metadataMatchesPythonClass() {
        PublishPRStage stage = new PublishPRStage();

        assertThat(stage.name()).isEqualTo("publish_pr");
        assertThat(stage.slot()).isEqualTo("publish");
        assertThat(stage.description()).isEqualTo("Push branch and create PR when configured.");
        assertThat(stage.consumes()).containsExactly("verify_report", "commit_result");
        assertThat(stage.produces()).containsExactly("pull_request", "task_result");
    }

    @Test
    void completionSummaryFormatsGateStatusFilesAndDelivery() {
        OptimizationTask task = OptimizationTask.builder().topic("publish").build();
        CommitFacts facts = CommitFacts.builder()
                .allowedFiles(List.of("a.py", "b.py", "c.py", "d.py", "e.py", "f.py"))
                .build();
        Map<String, Object> ci = Map.of(
                "gates",
                List.of(Map.of("name", "compile", "passed", true), Map.of("name", "test", "passed", false))
        );

        String summary = PublishPRStage.buildCompletionSummary(task, facts, ci, "https://gitcode/pr/1");

        assertThat(summary).contains("publish: 已完成");
        assertThat(summary).contains("compile=PASS, test=FAIL");
        assertThat(summary).contains("a.py, b.py, c.py, d.py, e.py 等 6 个文件");
        assertThat(summary).contains("https://gitcode/pr/1");
    }

    @Test
    void failedCommitResultYieldsFailedTaskResult() {
        TaskContext ctx = taskContext(new AutoHarnessConfig());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder().build());
        ctx.putArtifact("commit_result", CommitArtifact.builder()
                .committed(false)
                .error("commit failed")
                .build());
        PublishPRStage stage = new PublishPRStage();

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).isEqualTo("commit failed");
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.FAILED);
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertThat(taskResult.isSuccess()).isFalse();
    }

    @Test
    void localCommitCompletesWithoutPrDraftOrPush() {
        TaskContext ctx = taskContext(new AutoHarnessConfig());
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of("passed", true))
                .build());
        ctx.putArtifact("commit_result", committedArtifact());
        AtomicInteger agentCalls = new AtomicInteger();
        PublishPRStage stage = new PublishPRStage((config, workspace, rails) -> inputs -> {
            agentCalls.incrementAndGet();
            return List.of().iterator();
        });

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(agentCalls.get()).isZero();
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.SUCCESS);
        PullRequestArtifact pr = (PullRequestArtifact) result.getArtifacts().get("pull_request");
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertThat(pr.getPrUrl()).isEmpty();
        assertThat(taskResult.isSuccess()).isTrue();
        assertThat(result.getMessages()).contains("任务完成（本地提交）");
    }

    @Test
    void remotePublishRepairsDraftThenPushesAndCreatesPr() throws IOException, InterruptedException {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .workspace(tempDir.toString())
                .gitRemote("origin")
                .forkOwner("fork")
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        RecordingGitOperations git = new RecordingGitOperations();
        orchestrator.setGit(git);
        OptimizationTask task = OptimizationTask.builder().topic("publish topic").build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setWtPath(tempDir.resolve("wt").toString());
        runtime.setRelated(List.of(Experience.builder()
                .type(ExperienceType.INSIGHT)
                .topic("draft")
                .summary("use checklist")
                .build()));
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of("gates", List.of(Map.of("name", "compile", "passed", true))))
                .build());
        ctx.putArtifact("commit_result", committedArtifact());
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> secondQuery = new AtomicReference<>();
        PublishPRStage stage = new PublishPRStage((ignoredConfig, workspace, rails) -> inputs -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 2) {
                secondQuery.set(String.valueOf(inputs.get("query")));
                return List.of(new OutputSchema(
                        "message",
                        0,
                        Map.of("content", "{\"title\":\"Fix publish\",\"body\":\"body\\n/kind bug\",\"kind\":\"bug\"}")
                )).iterator();
            }
            return List.of(new OutputSchema("message", 0, Map.of("content", "{\"title\":\"\",\"body\":\"x\"}")))
                    .iterator();
        });

        List<Object> events = toList(stage.stream(ctx));
        StageResult result = lastStageResult(events);

        assertThat(attempts).hasValue(2);
        assertThat(secondQuery.get()).contains("上一次 PR draft 校验失败原因");
        assertThat(git.pushedBranch).isEqualTo("feature/publish");
        assertThat(git.prTitle).isEqualTo("Fix publish");
        assertThat(git.prBody).contains("/kind bug");
        PullRequestArtifact pr = (PullRequestArtifact) result.getArtifacts().get("pull_request");
        assertThat(pr.getPrUrl()).isEqualTo("https://gitcode/pr/42");
        assertThat(result.getMessages()).contains("PR draft 已生成: Fix publish", "PR 已创建: https://gitcode/pr/42");
        assertThat(events.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .map(OutputSchema::getPayload)
                .map(String::valueOf)
                .toList()).anySatisfy(payload -> assertThat(payload).contains("修正 PR draft (2/2)"));
        assertThat(ctx.getTask().getStatus()).isEqualTo(TaskStatus.SUCCESS);
    }

    private TaskContext taskContext(AutoHarnessConfig config) {
        AutoHarnessConfig resolved = config == null ? new AutoHarnessConfig() : config;
        resolved.setDataDir(tempDir.resolve("data").toString());
        resolved.setWorkspace(tempDir.toString());
        OptimizationTask task = OptimizationTask.builder()
                .topic("publish topic")
                .description("publish description")
                .build();
        return new TaskContext(new AutoHarnessOrchestrator(resolved), task, new TaskRuntime());
    }

    private static CommitArtifact committedArtifact() {
        return CommitArtifact.builder()
                .committed(true)
                .branchName("feature/publish")
                .lastCommitStat("commit stat")
                .facts(CommitFacts.builder()
                        .allowedFiles(List.of("openjiuwen/harness/a.py"))
                        .editedFiles(List.of("openjiuwen/harness/a.py"))
                        .diffStat("1 file changed")
                        .build())
                .build();
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static StageResult lastStageResult(List<Object> events) {
        return (StageResult) events.get(events.size() - 1);
    }

    private static final class RecordingGitOperations extends GitOperations {
        private String pushedBranch = "";
        private String prTitle = "";
        private String prBody = "";

        private RecordingGitOperations() {
            super("");
        }

        @Override
        public Map<String, Object> push(String branchName) {
            this.pushedBranch = branchName;
            return Map.of("success", true, "output", "pushed");
        }

        @Override
        public Map<String, Object> createPr(String title, String body, String headBranch) {
            this.prTitle = title;
            this.prBody = body;
            return Map.of("success", true, "pr_url", "https://gitcode/pr/42");
        }
    }
}
