/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.contexts.TaskRuntime;
import com.openjiuwen.auto_harness.infra.GitOperations;
import com.openjiuwen.auto_harness.orchestrator.AutoHarnessOrchestrator;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.CommitArtifact;
import com.openjiuwen.auto_harness.schema.CommitFacts;
import com.openjiuwen.auto_harness.schema.CycleResult;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PullRequestArtifact;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.VerifyReportArtifact;
import com.openjiuwen.auto_harness.stages.PublishPrStage;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Publish PR stage parity tests.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.auto_harness.stages.test_publish_pr_stage}.</p>
 */
class TestPublishPrStage {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String COMPLETE_BODY = "/kind bug\n\n"
            + "## Overview\nFix PR draft source.\n\n"
            + "## Changes\n- Generate PR draft before creation\n\n"
            + "## Verification\n- pytest passed\n\n"
            + "**Self-checklist**:\n"
            + "+ - [x] Tests";
    private static final String SIMPLIFIED_BODY = "/kind task\n\n"
            + "## Overview\nSimplified body.\n\n"
            + "## Changes\n- Small docs update\n\n"
            + "## Verification\n- lint/type-check passed\n\n"
            + "## Checklist\n- [x] tests";

    @TempDir
    Path tempDir;

    @Test
    void testPublishPrStageGeneratesDraftThenCreatesPr() {
        FakeGit git = new FakeGit();
        TaskContext ctx = buildCtx(git);
        PublishPrStage stage = new PublishPrStage(ignored -> new FakeDraftAgent());

        StageResult result = finalStageResult(stage.stream(ctx));

        PullRequestArtifact artifact =
                (PullRequestArtifact) result.getArtifacts().get("pull_request");
        assertEquals("https://gitcode.com/pr/1", artifact.getPrUrl());
        assertEquals(1, git.pushCalls);
        assertEquals("auto-harness/topic", git.pushedBranch);
        assertEquals(1, git.createPrCalls);
        assertEquals("fix(harness): align PR draft", git.createdTitle);
        assertEquals(COMPLETE_BODY, git.createdBody);
        assertEquals("auto-harness/topic", git.createdHeadBranch);
    }

    @Test
    void testPublishPrStageFailsWhenDraftIsInvalid() {
        FakeGit git = new FakeGit();
        TaskContext ctx = buildCtx(git);
        BrokenDraftAgent agent = new BrokenDraftAgent();
        PublishPrStage stage = new PublishPrStage(ignored -> agent);

        StageResult result = finalStageResult(stage.stream(ctx));

        assertEquals("failed", result.getStatus());
        assertEquals(2, agent.calls);
        assertEquals(0, git.pushCalls);
        assertEquals(0, git.createPrCalls);
        CycleResult taskResult = (CycleResult) result.getArtifacts().get("task_result");
        assertTrue(taskResult.getError().contains("PR draft generation failed after 2 attempts"));
        assertTrue(taskResult.getError().contains("JSON"));
    }

    @Test
    void testPublishPrStageAcceptsSimplifiedPrBody() {
        FakeGit git = new FakeGit();
        TaskContext ctx = buildCtx(git);
        PublishPrStage stage = new PublishPrStage(ignored -> new SimplifiedDraftAgent());

        StageResult result = finalStageResult(stage.stream(ctx));

        PullRequestArtifact artifact =
                (PullRequestArtifact) result.getArtifacts().get("pull_request");
        assertEquals("https://gitcode.com/pr/1", artifact.getPrUrl());
        assertEquals(1, git.pushCalls);
        assertEquals(1, git.createPrCalls);
        assertEquals("docs(cli): add test line to README", git.createdTitle);
        assertEquals(SIMPLIFIED_BODY, git.createdBody);
    }

    @Test
    void testPublishPrStageDoesNotRetryAfterSimplifiedDraft() {
        FakeGit git = new FakeGit();
        TaskContext ctx = buildCtx(git);
        RepairingDraftAgent agent = new RepairingDraftAgent();
        PublishPrStage stage = new PublishPrStage(ignored -> agent);

        StageResult result = finalStageResult(stage.stream(ctx));

        PullRequestArtifact artifact =
                (PullRequestArtifact) result.getArtifacts().get("pull_request");
        assertEquals("https://gitcode.com/pr/1", artifact.getPrUrl());
        assertEquals(1, agent.calls);
        assertFalse(agent.queries.get(0).contains("Previous PR draft validation failure"));
        assertEquals(1, git.pushCalls);
        assertEquals(1, git.createPrCalls);
    }

    private TaskContext buildCtx(FakeGit git) {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.toString());
        config.setWorkspace(tempDir.toString());
        config.setGitRemote("origin");
        config.setForkOwner("bot");
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config, null);
        orchestrator.setGit(git);

        TaskRuntime runtime = new TaskRuntime();
        runtime.setWtPath(tempDir.toString());
        TaskContext ctx = new TaskContext(
                orchestrator,
                new OptimizationTask("Fix PR draft"),
                runtime
        );

        ctx.putArtifact("verify_report", new VerifyReportArtifact(Map.of(
                "passed", true,
                "gates", List.of(Map.of("name", "lint", "passed", true))
        )));

        CommitFacts facts = new CommitFacts();
        facts.setBranchName("auto-harness/topic");
        facts.setAllowedFiles(List.of("openjiuwen/harness/demo.py"));
        facts.setEditedFiles(List.of("openjiuwen/harness/demo.py"));
        facts.setDiffStat(" demo.py | 2 +-");

        CommitArtifact commit = new CommitArtifact();
        commit.setFacts(facts);
        commit.setBranchName("auto-harness/topic");
        commit.setLastCommitStat("commit abc123\n demo.py | 2 +-");
        commit.setCommitted(true);
        ctx.putArtifact("commit_result", commit);
        return ctx;
    }

    private static StageResult finalStageResult(Iterator<Object> iterator) {
        StageResult last = null;
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof StageResult stageResult) {
                last = stageResult;
            }
        }
        assertNotNull(last);
        return last;
    }

    static class FakeGit extends GitOperations {
        int pushCalls;
        int createPrCalls;
        String pushedBranch;
        String createdTitle;
        String createdBody;
        String createdHeadBranch;

        FakeGit() {
            super("");
        }

        @Override
        public Map<String, Object> push(String branchName) {
            pushCalls++;
            pushedBranch = branchName;
            return Map.of("success", true);
        }

        @Override
        public Map<String, Object> createPr(String title, String body, String headBranch) {
            createPrCalls++;
            createdTitle = title;
            createdBody = body;
            createdHeadBranch = headBranch;
            return Map.of("success", true, "pr_url", "https://gitcode.com/pr/1");
        }
    }

    static class FakeDraftAgent implements PublishPrStage.DraftAgent {
        @Override
        public Iterator<Object> stream(Map<String, Object> payload) {
            assertTrue(String.valueOf(payload.get("query")).contains("Task topic: Fix PR draft"));
            return one(jsonOutput(
                    "fix(harness): align PR draft",
                    "bug",
                    COMPLETE_BODY
            ));
        }
    }

    static class BrokenDraftAgent implements PublishPrStage.DraftAgent {
        int calls;

        @Override
        public Iterator<Object> stream(Map<String, Object> payload) {
            calls++;
            return one(new OutputSchema(
                    "llm_output",
                    0,
                    Map.of("content", "not-json")
            ));
        }
    }

    static class SimplifiedDraftAgent implements PublishPrStage.DraftAgent {
        @Override
        public Iterator<Object> stream(Map<String, Object> payload) {
            return one(jsonOutput(
                    "docs(cli): add test line to README",
                    "task",
                    SIMPLIFIED_BODY
            ));
        }
    }

    static class RepairingDraftAgent implements PublishPrStage.DraftAgent {
        int calls;
        List<String> queries = new ArrayList<>();

        @Override
        public Iterator<Object> stream(Map<String, Object> payload) {
            calls++;
            queries.add(String.valueOf(payload.get("query")));
            if (calls == 1) {
                return one(jsonOutput(
                        "docs(cli): add test line to README",
                        "task",
                        SIMPLIFIED_BODY
                ));
            }
            return one(jsonOutput(
                    "docs(cli): expand auto-harness test docs",
                    "task",
                    COMPLETE_BODY
            ));
        }
    }

    private static Iterator<Object> one(Object value) {
        return List.of(value).iterator();
    }

    private static OutputSchema jsonOutput(String title, String kind, String body) {
        try {
            String json = MAPPER.writeValueAsString(Map.of(
                    "title", title,
                    "kind", kind,
                    "body", body
            ));
            return new OutputSchema("llm_output", 0, Map.of("content", "```json\n" + json + "\n```"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
