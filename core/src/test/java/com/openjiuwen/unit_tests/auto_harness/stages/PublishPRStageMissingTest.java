/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CommitFacts;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PullRequestArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.stages.PublishPRStage;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests/unit_tests/auto_harness/stages/test_publish_pr_stage.py}.
 */
class PublishPRStageMissingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FULL_TEMPLATE_BODY = """
            <!--  Thanks for sending a pull request!  Here are some tips for you:

            1) If this is your first time, please read our contributor guidelines: https://gitcode.com/openJiuwen/community/blob/master/CONTRIBUTING.md

            2) If you want to contribute your code but don't know who will review and merge, please add label `openjiuwen-assistant` to the pull request, we will find and do it as soon as possible.
            -->

            **What type of PR is this?**
            <!--
            选择下面一种标签替换下方 `/kind <label>`，可选标签类型有：
            - /kind bug
            - /kind task
            - /kind feature
            - /kind refactor
            - /kind clean_code
            如PR描述不符合规范，修改PR描述后需要/check-pr重新检查PR规范。
            -->
            /kind bug

            ## 概述
            修复 PR 文案来源。

            ## 变更内容
            - publish_pr 阶段先生成 PR draft

            ## 验证结果
            - pytest passed

            **Self-checklist**:（**请自检，在[ ]内打上x，我们将检视你的完成情况，否则会导致pr无法合入**）

            + - [ ] **设计**：PR对应的方案是否已经经过Maintainer评审，方案检视意见是否均已答复并完成方案修改
            + - [x] **测试**：PR中的代码是否已有UT/ST测试用例进行充分的覆盖，新增测试用例是否随本PR一并上库或已经上库
            + - [x] **验证**：PR描述信息中是否已包含对该PR对应的Feature、Refactor、Bugfix的预期目标达成情况的详细验证结果描述
            + - [ ] **接口**：是否涉及对外接口变更，相应变更已得到接口评审组织的通过，API对应的注释信息已经刷新正确
            + - [ ] **文档**：是否涉及官网文档修改，如果涉及请及时提交资料到Doc仓""";
    private static final String SIMPLIFIED_BODY = """
            /kind task

            ## 概述
            简化版 body。

            ## 变更内容
            - 只写简化说明

            ## 验证结果
            - lint/type-check passed

            ## Checklist
            - [x] 测试""";

    @TempDir
    Path tempDir;

    @Test
    void publishPrStageGeneratesDraftThenCreatesPr() {
        RecordingGitOperations git = new RecordingGitOperations();
        RecordingDraftAgent agent = new RecordingDraftAgent(
                fencedDraft("fix(harness): 补齐 PR draft", "bug", FULL_TEMPLATE_BODY)
        );
        TaskContext ctx = taskContext(git);
        PublishPRStage stage = new PublishPRStage((config, workspace, rails) -> agent);

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(agent.queries).hasSize(1);
        assertThat(agent.queries.get(0)).contains("任务主题: 修复 PR draft");
        assertThat(((PullRequestArtifact) result.getArtifacts().get("pull_request")).getPrUrl())
                .isEqualTo("https://gitcode.com/pr/1");
        assertThat(git.pushedBranches).containsExactly("auto-harness/topic");
        assertThat(git.prTitles).containsExactly("fix(harness): 补齐 PR draft");
        assertThat(git.prBodies).containsExactly(FULL_TEMPLATE_BODY);
        assertThat(git.headBranches).containsExactly("auto-harness/topic");
    }

    @Test
    void publishPrStageFailsWhenDraftIsInvalid() {
        RecordingGitOperations git = new RecordingGitOperations();
        RecordingDraftAgent agent = new RecordingDraftAgent("not-json", "not-json");
        TaskContext ctx = taskContext(git);
        PublishPRStage stage = new PublishPRStage((config, workspace, rails) -> agent);

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(agent.queries).hasSize(2);
        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getError()).isEqualTo("PR draft generation failed after 2 attempts: 未找到 JSON 对象");
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertThat(taskResult.isSuccess()).isFalse();
        assertThat(taskResult.getError()).isEqualTo("PR draft generation failed after 2 attempts: 未找到 JSON 对象");
        assertThat(git.pushedBranches).isEmpty();
        assertThat(git.prTitles).isEmpty();
    }

    @Test
    void publishPrStageAcceptsSimplifiedPrBody() {
        RecordingGitOperations git = new RecordingGitOperations();
        RecordingDraftAgent agent = new RecordingDraftAgent(
                fencedDraft("docs(cli): add test line to README", "task", SIMPLIFIED_BODY)
        );
        TaskContext ctx = taskContext(git);
        PublishPRStage stage = new PublishPRStage((config, workspace, rails) -> agent);

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(agent.queries).hasSize(1);
        assertThat(((PullRequestArtifact) result.getArtifacts().get("pull_request")).getPrUrl())
                .isEqualTo("https://gitcode.com/pr/1");
        assertThat(git.pushedBranches).containsExactly("auto-harness/topic");
        assertThat(git.prTitles).containsExactly("docs(cli): add test line to README");
        assertThat(git.prBodies).containsExactly(SIMPLIFIED_BODY);
        assertThat(git.headBranches).containsExactly("auto-harness/topic");
    }

    @Test
    void publishPrStageDoesNotRetryAfterSimplifiedDraft() {
        RecordingGitOperations git = new RecordingGitOperations();
        RepairingDraftAgent agent = new RepairingDraftAgent();
        TaskContext ctx = taskContext(git);
        PublishPRStage stage = new PublishPRStage((config, workspace, rails) -> agent);

        StageResult result = lastStageResult(toList(stage.stream(ctx)));

        assertThat(agent.calls).hasValue(1);
        assertThat(((PullRequestArtifact) result.getArtifacts().get("pull_request")).getPrUrl())
                .isEqualTo("https://gitcode.com/pr/1");
        assertThat(git.pushedBranches).containsExactly("auto-harness/topic");
        assertThat(git.prTitles).containsExactly("docs(cli): add test line to README");
    }

    private TaskContext taskContext(RecordingGitOperations git) {
        AutoHarnessConfig config = AutoHarnessConfig.builder()
                .dataDir(tempDir.resolve("data").toString())
                .workspace(tempDir.toString())
                .gitRemote("origin")
                .forkOwner("bot")
                .build();
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);
        orchestrator.setGit(git);
        OptimizationTask task = OptimizationTask.builder()
                .topic("修复 PR draft")
                .build();
        TaskRuntime runtime = new TaskRuntime();
        runtime.setWtPath(tempDir.resolve("worktree").toString());
        TaskContext ctx = new TaskContext(orchestrator, task, runtime);
        ctx.putArtifact("verify_report", VerifyReportArtifact.builder()
                .ciResult(Map.of(
                        "passed", true,
                        "gates", List.of(Map.of("name", "lint", "passed", true))
                ))
                .build());
        ctx.putArtifact("commit_result", CommitArtifact.builder()
                .facts(CommitFacts.builder()
                        .branchName("auto-harness/topic")
                        .allowedFiles(List.of("openjiuwen/harness/demo.py"))
                        .editedFiles(List.of("openjiuwen/harness/demo.py"))
                        .diffStat(" demo.py | 2 +-")
                        .build())
                .branchName("auto-harness/topic")
                .lastCommitStat("commit abc123\n demo.py | 2 +-")
                .committed(true)
                .build());
        return ctx;
    }

    private static String fencedDraft(String title, String kind, String body) {
        try {
            return "```json\n" + MAPPER.writeValueAsString(Map.of(
                    "title", title,
                    "kind", kind,
                    "body", body
            )) + "\n```";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build draft JSON", e);
        }
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static StageResult lastStageResult(List<Object> events) {
        return (StageResult) events.get(events.size() - 1);
    }

    private static final class RecordingDraftAgent implements PublishPRStage.PrDraftAgent {
        private final List<String> outputs;
        private final List<String> queries = new ArrayList<>();
        private int index;

        private RecordingDraftAgent(String... outputs) {
            this.outputs = List.of(outputs);
        }

        @Override
        public Iterator<?> stream(Map<String, Object> inputs) {
            queries.add(String.valueOf(inputs.get("query")));
            String output = outputs.get(Math.min(index, outputs.size() - 1));
            index++;
            return List.of(new OutputSchema("llm_output", 0, Map.of("content", output))).iterator();
        }
    }

    private static final class RepairingDraftAgent implements PublishPRStage.PrDraftAgent {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Iterator<?> stream(Map<String, Object> inputs) {
            int attempt = calls.incrementAndGet();
            if (attempt == 1) {
                assertThat(String.valueOf(inputs.get("query"))).doesNotContain("上一次 PR draft 校验失败原因");
                return List.of(new OutputSchema(
                        "llm_output",
                        0,
                        Map.of("content", fencedDraft(
                                "docs(cli): add test line to README",
                                "task",
                                SIMPLIFIED_BODY
                        ))
                )).iterator();
            }
            assertThat(String.valueOf(inputs.get("query"))).contains("上一次 PR draft 校验失败原因");
            return List.of(new OutputSchema(
                    "llm_output",
                    0,
                    Map.of("content", fencedDraft("docs(cli): 补充 auto-harness 测试说明", "task", FULL_TEMPLATE_BODY))
            )).iterator();
        }
    }

    private static final class RecordingGitOperations extends GitOperations {
        private final List<String> pushedBranches = new ArrayList<>();
        private final List<String> prTitles = new ArrayList<>();
        private final List<String> prBodies = new ArrayList<>();
        private final List<String> headBranches = new ArrayList<>();

        private RecordingGitOperations() {
            super("");
        }

        @Override
        public Map<String, Object> push(String branchName) {
            pushedBranches.add(branchName);
            return Map.of("success", true, "output", "pushed");
        }

        @Override
        public Map<String, Object> createPr(String title, String body, String headBranch) {
            prTitles.add(title);
            prBodies.add(body);
            headBranches.add(headBranch);
            return Map.of("success", true, "pr_url", "https://gitcode.com/pr/1");
        }
    }
}
