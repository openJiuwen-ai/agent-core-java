/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.experience.EvolutionContext;
import com.openjiuwen.agent_evolving.experience.OnlineEvolutionContext;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supplemental parity tests for team skill optimizer test coverage.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/optimizer/test_team_skill_optimizer.py}.</p>
 */
class TeamSkillExperienceOptimizerPythonParityTest {

    private static final String CURRENT_SKILL_CONTENT = "# Team Skill\n## Workflow\nKeep the reviewer handoff explicit.";

    @Test
    void teamSkillOptimizerCompatAliasPointsToFormalClass() {
        assertEquals(TeamSkillExperienceOptimizer.class, TeamSkillOptimizer.class.getSuperclass());
    }

    @Test
    void userPatchPromptCnExists() {
        String prompt = SkillCallTemplates.USER_PATCH_PROMPT.get("cn");

        assertNotNull(prompt);
        assertTrue(prompt.contains("用户意见"));
        assertTrue(prompt.contains("已有演进经验摘要"));
        assertTrue(prompt.contains("相关性判断"));
        assertTrue(prompt.contains("缺少交接"));
        assertTrue(prompt.contains("section 选择参考"));
        assertTrue(prompt.contains("只生成一条 patch"));
        assertTrue(prompt.contains("避免“加强协作”“优化流程”这类空话"));
        assertTrue(prompt.contains("need_patch"));
        assertTrue(prompt.contains("\"summary\""));
        assertTrue(prompt.contains("duplicate | irrelevant | low_value"));
    }

    @Test
    void userPatchPromptEnExists() {
        String prompt = SkillCallTemplates.USER_PATCH_PROMPT.get("en");

        assertNotNull(prompt);
        assertTrue(prompt.contains("Existing evolution summary"));
        assertTrue(prompt.contains("Relevance"));
        assertTrue(prompt.contains("missing handoffs"));
        assertTrue(prompt.contains("Section mapping guide"));
        assertTrue(prompt.contains("Generate exactly one patch"));
        assertTrue(prompt.contains("avoid vague statements like \"improve collaboration\""));
        assertTrue(prompt.contains("need_patch=false"));
        assertTrue(prompt.contains("\"summary\""));
    }

    @Test
    void trajectoryPatchPromptCnExists() {
        String prompt = SkillCallTemplates.TRAJECTORY_PATCH_PROMPT.get("cn");

        assertNotNull(prompt);
        assertTrue(prompt.contains("轨迹分析") || prompt.contains("执行轨迹"));
        assertTrue(prompt.contains("已有演进经验摘要"));
        assertTrue(prompt.contains("相关性"));
        assertTrue(prompt.contains("去重性"));
        assertTrue(prompt.contains("优先级"));
        assertTrue(prompt.contains("section 选择参考"));
        assertTrue(prompt.contains("只输出一条 patch"));
        assertTrue(prompt.contains("\"summary\""));
        assertTrue(prompt.contains("失败恢复"));
    }

    @Test
    void trajectoryPatchPromptEnExists() {
        String prompt = SkillCallTemplates.TRAJECTORY_PATCH_PROMPT.get("en");

        assertNotNull(prompt);
        assertTrue(prompt.contains("Existing evolution summary"));
        assertTrue(prompt.contains("Relevance"));
        assertTrue(prompt.contains("Deduplication"));
        assertTrue(prompt.contains("Priority"));
        assertTrue(prompt.contains("Section mapping guide"));
        assertTrue(prompt.contains("Output exactly one patch"));
        assertTrue(prompt.contains("\"summary\""));
        assertTrue(prompt.contains("Missing recovery paths"));
    }

    @Test
    void teamAggregatedPromptsRequestSummaryField() {
        for (String prompt : SkillCallTemplates.TEAM_EXPERIENCE_GENERATE_PROMPT.values()) {
            assertTrue(prompt.contains("\"summary\""));
            assertTrue(prompt.toLowerCase().contains("summary"));
        }
    }

    @Test
    void recordLlmPolicyPropertyReturnsConfiguredPolicy() {
        LlmResilience.LLMInvokePolicy policy = new LlmResilience.LLMInvokePolicy(15, 45, 2, 0, true);
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(
                new Model(new RecordingInvoker()),
                "test-model",
                "en",
                null,
                policy,
                null
        );

        assertSame(policy, optimizer.getRecordLlmPolicy());
    }

    @Test
    void generateUserPatchReturnsRecordOnValidResponse() {
        RecordingInvoker invoker = new RecordingInvoker("""
                {"section":"Collaboration","action":"append","summary":"Require role A to notify role B before handoff.","content":"角色 A 完成后通知角色 B"}
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn");

        EvolutionRecord record = optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "改进协作流程");

        assertNotNull(record);
        assertEquals("Collaboration", record.getChange().getSection());
        assertEquals("Require role A to notify role B before handoff.", record.getSummary());
        assertTrue(record.getChange().getContent().contains("角色 A 完成后通知角色 B"));
        assertEquals("team_skill_user_patch", record.getSource());
        assertEquals(120.0f, invoker.options().getFirst().getTimeout());
    }

    @Test
    void generateUserPatchRaisesOnEmptyResponse() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("", "", ""), "cn");

        assertThrows(BaseError.class, () -> optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "test intent"));
    }

    @Test
    void generateUserPatchRaisesOnInvalidJson() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("not json at all", "not json either", "broken"), "cn");

        assertThrows(BaseError.class, () -> optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "test intent"));
    }

    @Test
    void generateUserPatchRaisesOnEmptyContent() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("""
                {"section":"Instructions","action":"append","content":""}
                """), "cn");

        assertThrows(IllegalArgumentException.class,
                () -> optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "test intent"));
    }

    @Test
    void generateUserPatchRetriesWhenFirstResponseIsInvalidJson() {
        RecordingInvoker invoker = new RecordingInvoker(
                "not json",
                "{\"section\":\"Collaboration\",\"action\":\"append\",\"content\":\"先同步上下文，再分派角色\"}"
        );
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn");

        EvolutionRecord record = optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "优化协作");

        assertNotNull(record);
        assertEquals("Collaboration", record.getChange().getSection());
        assertEquals(2, invoker.prompts().size());
    }

    @Test
    void generateUserPatchRetriesWhenFirstResponseIsJsonArrayNotPatchObject() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"section\":\"Collaboration\",\"action\":\"append\",\"content\":\"not an object\"}]",
                "{\"section\":\"Collaboration\",\"action\":\"append\",\"content\":\"先同步上下文，再分派角色\"}"
        );
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn");

        EvolutionRecord record = optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "优化协作");

        assertNotNull(record);
        assertEquals("Collaboration", record.getChange().getSection());
        assertEquals(2, invoker.prompts().size());
    }

    @Test
    void generateUserPatchReturnsNullWhenNeedPatchFalse() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("""
                {"need_patch":false,"section":"","action":"skip","content":"","reason":"duplicate"}
                """), "cn");

        assertNull(optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "重复已有协作约束"));
    }

    @Test
    void generateUserPromptIncludesExistingEvolutionsAndSkillContentWhenStoreAvailable(@TempDir Path tempDir)
            throws Exception {
        EvolutionStore store = storeWithSkillAndRecord(tempDir, "test-skill");
        RecordingInvoker invoker = new RecordingInvoker("""
                {"need_patch":true,"section":"Constraints","action":"append","content":"新增质量门","reason":"new_learning"}
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn", store);

        EvolutionRecord record = optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "补充质量门");

        assertNotNull(record);
        String prompt = invoker.prompts().getFirst();
        assertTrue(prompt.contains("## 当前 Team Skill 正文"));
        assertTrue(prompt.contains("Existing body"));
        assertTrue(prompt.contains("已有演进经验摘要"));
        assertTrue(prompt.contains("ev_12345678"));
    }

    @Test
    void generateUserPatchRetriesWithShorterPromptAfterTimeout() {
        RecordingInvoker invoker = new RecordingInvoker(
                new TimeoutException("request timed out"),
                "{\"section\":\"Collaboration\",\"action\":\"append\",\"content\":\"先同步上下文，再分派角色\"}"
        );
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn", shortPolicy());

        EvolutionRecord record = optimizer.generateUserPatch(toolTrajectory(), "test-skill", "优化协作 ".repeat(400));

        assertNotNull(record);
        assertTrue(invoker.prompts().get(1).length() < invoker.prompts().get(0).length());
    }

    @Test
    void generateTrajectoryPatchReturnsRecordWhenNeedPatchTrue() {
        RecordingInvoker invoker = new RecordingInvoker("""
                {"need_patch":true,"section":"Constraints","summary":"Cap execution timeout to avoid repeated stalls.","content":"执行超时不得超过 30 秒","reason":"轨迹显示多次超时"}
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn");

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                emptyTrajectory(),
                "test-skill",
                CURRENT_SKILL_CONTENT,
                List.of(Map.of("issue_type", "timeout", "description", "多次超时", "severity", "high"))
        );

        assertNotNull(record);
        assertEquals("Constraints", record.getChange().getSection());
        assertEquals("Cap execution timeout to avoid repeated stalls.", record.getSummary());
        assertTrue(record.getChange().getContent().contains("执行超时不得超过 30 秒"));
        assertEquals("team_skill_trajectory_patch", record.getSource());
        assertEquals(120.0f, invoker.options().getFirst().getTimeout());
        assertTrue(invoker.prompts().getFirst().contains(CURRENT_SKILL_CONTENT));
    }

    @Test
    void generateTrajectoryPromptIncludesExistingEvolutionsWhenStoreAvailable(@TempDir Path tempDir) throws Exception {
        EvolutionStore store = storeWithSkillAndRecord(tempDir, "test-skill");
        RecordingInvoker invoker = new RecordingInvoker("""
                {"need_patch":true,"section":"Collaboration","content":"补充交接确认","reason":"new_learning"}
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn", store);

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                emptyTrajectory(),
                "test-skill",
                CURRENT_SKILL_CONTENT,
                List.of(Map.of("issue_type", "handoff", "description", "缺少确认", "severity", "high"))
        );

        assertNotNull(record);
        String prompt = invoker.prompts().getFirst();
        assertTrue(prompt.contains("已有演进经验摘要"));
        assertTrue(prompt.contains("ev_12345678"));
    }

    @Test
    void generateTrajectoryPatchUsesCustomRecordLlmPolicy() {
        RecordingInvoker invoker = new RecordingInvoker("""
                {"need_patch":true,"section":"Workflow","content":"Coordinate the reviewer handoff.","reason":"Observed repeated context loss."}
                """);
        LlmResilience.LLMInvokePolicy policy = new LlmResilience.LLMInvokePolicy(15, 45, 2, 0, true);
        TeamSkillOptimizer optimizer = optimizer(invoker, "en", policy);

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                emptyTrajectory(),
                "test-skill",
                CURRENT_SKILL_CONTENT,
                List.of(Map.of("issue_type", "handoff"))
        );

        assertNotNull(record);
        assertEquals(15.0f, invoker.options().getFirst().getTimeout());
    }

    @Test
    void generateTrajectoryPatchReturnsNullWhenNeedPatchFalse() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("{\"need_patch\":false,\"reason\":\"轨迹无异常\"}"), "cn");

        assertNull(optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", CURRENT_SKILL_CONTENT, List.of()));
    }

    @Test
    void generateTrajectoryPatchRaisesOnEmptyResponse() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("", "", ""), "cn");

        assertThrows(BaseError.class,
                () -> optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", CURRENT_SKILL_CONTENT, List.of()));
    }

    @Test
    void generateTrajectoryPatchRaisesOnInvalidJson() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("broken json", "still broken", "bad"), "cn");

        assertThrows(BaseError.class,
                () -> optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", CURRENT_SKILL_CONTENT, List.of()));
    }

    @Test
    void generateTrajectoryPatchRaisesOnEmptyContent() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker("""
                {"need_patch":true,"section":"Workflow","content":"   "}
                """), "cn");

        assertThrows(IllegalArgumentException.class,
                () -> optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", CURRENT_SKILL_CONTENT, List.of()));
    }

    @Test
    void generateTrajectoryPatchRetriesWithShorterPromptAfterTimeout() {
        RecordingInvoker invoker = new RecordingInvoker(
                new TimeoutException("request timed out"),
                "{\"need_patch\":true,\"section\":\"Workflow\",\"content\":\"失败后先收敛上下文，再重试\",\"reason\":\"轨迹过长\"}"
        );
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn", shortPolicy());

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                toolTrajectory(),
                "test-skill",
                CURRENT_SKILL_CONTENT.repeat(500),
                List.of(Map.of("issue_type", "timeout", "description", "超时 ".repeat(1000), "severity", "high"))
        );

        assertNotNull(record);
        assertTrue(invoker.prompts().get(1).length() < invoker.prompts().get(0).length());
    }

    @Test
    void generateRecordsReturnsEmptyWhenNoSignals() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker(), "cn");

        assertEquals(List.of(), optimizer.generateRecords(defaultContext(List.of())));
    }

    @Test
    void generateRecordsReraisesLlmBaseError() {
        RecordingInvoker invoker = new RecordingInvoker(new BaseError(
                StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                Map.<String, Object>of("error_msg", "network failed")
        ));
        TeamSkillOptimizer optimizer = optimizer(invoker, "cn");

        assertThrows(BaseError.class, () -> optimizer.generateRecords(defaultContext(List.of(makeSignal("trajectory_issue")))));
    }

    @Test
    void generateRecordsAggregatesUserAndTrajectoryRecordsFromContext() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [
                  {"action":"append","target":"body","section":"Instructions","content":"User record","summary":"user"},
                  {"action":"append","target":"body","section":"Workflow","content":"Trajectory record","summary":"traj"}
                ]
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(
                makeSignal("user_intent"),
                makeSignal("trajectory_issue")
        )));

        assertEquals(List.of("user", "traj"), records.stream().map(EvolutionRecord::getSummary).toList());
        assertEquals("team_skill_mixed", records.getFirst().getSource());
    }

    @Test
    void backwardRoutesThroughGenerateRecords() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [{"action":"append","target":"body","section":"Workflow","content":"A"}]
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");
        TestOperator operator = new TestOperator("skill_experience_test-skill");
        EvolutionSignal signal = makeSignal("user_intent");
        EvolutionContext onlineContext = defaultContext(List.of(signal));

        optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                List.of(Protocols.EXPERIENCES_TARGET),
                Map.of("online_contexts", Map.of("test-skill", onlineContext))
        );
        optimizer.addTrajectory(emptyTrajectory());
        optimizer.backward(List.of(signal)).toCompletableFuture().join();

        Object gradient = optimizer.parameters().get(operator.getOperatorId()).getGradient(Protocols.EXPERIENCES_TARGET);
        assertInstanceOf(List.class, gradient);
        assertEquals(1, ((List<?>) gradient).size());
        assertTrue(invoker.prompts().getFirst().contains("# Team Skill"));
    }

    @Test
    void backwardPrefersExplicitOnlineContext() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [{"action":"append","target":"body","section":"Workflow","content":"A"}]
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");
        TestOperator operator = new TestOperator("skill_experience_test-skill");
        EvolutionSignal signal = makeSignal("user_intent");
        OnlineEvolutionContext onlineContext = new OnlineEvolutionContext(defaultContext(List.of(signal)));
        onlineContext.setSkillContent("# Context Team Skill");
        onlineContext.setUserQuery("context query");

        optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                List.of(Protocols.EXPERIENCES_TARGET),
                Map.of("online_contexts", Map.of("test-skill", onlineContext))
        );
        optimizer.backward(List.of(signal)).toCompletableFuture().join();

        assertTrue(invoker.prompts().getFirst().contains("# Context Team Skill"));
        assertTrue(invoker.prompts().getFirst().contains("context query"));
    }

    @Test
    void backwardRaisesClearErrorWithoutOnlineContext() {
        TeamSkillOptimizer optimizer = optimizer(new RecordingInvoker(), "en");
        TestOperator operator = new TestOperator("skill_experience_test-skill");
        optimizer.bind(Map.of(operator.getOperatorId(), operator), List.of(Protocols.EXPERIENCES_TARGET), Map.of());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> optimizer.backward(List.of(makeSignal("user_intent"))).toCompletableFuture().join());

        assertTrue(rootCause(exception).getMessage().contains("online_contexts missing entry for skill test-skill"));
    }

    @Test
    void backwardDoesNotMutateOnlineContextWhenFillingDefaultTrajectory() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [{"action":"append","target":"body","section":"Workflow","content":"A"}]
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");
        TestOperator operator = new TestOperator("skill_experience_test-skill");
        EvolutionSignal signal = makeSignal("trajectory_issue");
        OnlineEvolutionContext onlineContext = new OnlineEvolutionContext(defaultContext(List.of(signal)));
        onlineContext.setTrajectory(null);

        optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                List.of(Protocols.EXPERIENCES_TARGET),
                Map.of("online_contexts", Map.of("test-skill", onlineContext))
        );
        optimizer.addTrajectory(Trajectory.builder().executionId("exec-3").sessionId("session-3").source("online").build());
        optimizer.backward(List.of(signal)).toCompletableFuture().join();

        assertNull(onlineContext.getTrajectory());
        Object gradient = optimizer.parameters().get(operator.getOperatorId()).getGradient(Protocols.EXPERIENCES_TARGET);
        assertInstanceOf(List.class, gradient);
    }

    @Test
    void generateRepairsMalformedJsonAndEmitsDescriptionTarget() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"description\",\"section\":\"Instructions\",\"content\":\"bad\",]",
                "[{\"action\":\"append\",\"target\":\"description\",\"section\":\"Instructions\",\"content\":\"Add clearer team applicability wording.\"}]"
        );
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal("user_intent"))));

        assertEquals(1, records.size());
        assertEquals(EvolutionTarget.DESCRIPTION, records.getFirst().getChange().getTarget());
        assertEquals("Instructions", records.getFirst().getChange().getSection());
        assertEquals(2, invoker.prompts().size());
    }

    @Test
    void generateRegeneratesWhenOutputIsTruncated() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Workflow\",\"content\":\"cut off\"",
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Workflow\",\"content\":\"### Workflow\\n- Add an explicit handoff gate.\"}]"
        );
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal("trajectory_issue"))));

        assertEquals(1, records.size());
        assertEquals("Workflow", records.getFirst().getChange().getSection());
        assertEquals(invoker.prompts().get(0), invoker.prompts().get(1));
    }

    @Test
    void generateSupportsScriptTargetAndLimitsTextRecords() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [
                  {"action":"append","target":"body","section":"Workflow","summary":"Add a handoff gate before review starts.","content":"### Workflow\\n- Rule A"},
                  {"action":"append","target":"description","section":"Instructions","content":"Clarify the team specializes in review-heavy tasks."},
                  {"action":"append","target":"body","section":"Collaboration","content":"### Collaboration\\n- Rule C"},
                  {"action":"append","target":"script","section":"Scripts","summary":"Audit team handoff completeness with a helper script.","content":"print('hello')","script_filename":"handoff_audit.py","script_language":"python","script_purpose":"audit handoff completeness"}
                ]
                """);
        TeamSkillOptimizer optimizer = optimizer(invoker, "en");

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal("trajectory_issue"))));

        assertEquals(3, records.size());
        List<EvolutionRecord> textRecords = records.stream()
                .filter(record -> record.getChange().getTarget() != EvolutionTarget.SCRIPT)
                .toList();
        List<EvolutionRecord> scriptRecords = records.stream()
                .filter(record -> record.getChange().getTarget() == EvolutionTarget.SCRIPT)
                .toList();
        assertEquals(2, textRecords.size());
        assertEquals(1, scriptRecords.size());
        assertEquals("Add a handoff gate before review starts.", textRecords.getFirst().getSummary());
        assertEquals("Audit team handoff completeness with a helper script.", scriptRecords.getFirst().getSummary());
        assertEquals("handoff_audit.py", scriptRecords.getFirst().getChange().getScriptFilename());
    }

    private static TeamSkillOptimizer optimizer(RecordingInvoker invoker, String language) {
        return optimizer(invoker, language, null, null);
    }

    private static TeamSkillOptimizer optimizer(
            RecordingInvoker invoker,
            String language,
            LlmResilience.LLMInvokePolicy policy
    ) {
        return optimizer(invoker, language, policy, null);
    }

    private static TeamSkillOptimizer optimizer(RecordingInvoker invoker, String language, EvolutionStore store) {
        return optimizer(invoker, language, null, store);
    }

    private static TeamSkillOptimizer optimizer(
            RecordingInvoker invoker,
            String language,
            LlmResilience.LLMInvokePolicy policy,
            EvolutionStore store
    ) {
        return new TeamSkillOptimizer(
                new Model(invoker),
                "test-model",
                language,
                null,
                policy == null ? TeamSkillExperienceOptimizer.TEAM_SKILL_RECORD_LLM_POLICY : policy,
                store
        );
    }

    private static LlmResilience.LLMInvokePolicy shortPolicy() {
        return new LlmResilience.LLMInvokePolicy(15, 45, 2, 0, true);
    }

    private static Trajectory emptyTrajectory() {
        return Trajectory.builder()
                .executionId("team-skill-evolution")
                .sessionId("team-skill-evolution")
                .source("online")
                .steps(List.of())
                .build();
    }

    private static Trajectory toolTrajectory() {
        return Trajectory.builder()
                .executionId("team-skill-evolution")
                .sessionId("team-skill-evolution")
                .source("online")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("tool")
                        .detail(ToolCallDetail.builder()
                                .toolName("spawn_member")
                                .callArgs("arg ".repeat(1000))
                                .callResult("result ".repeat(1000))
                                .build())
                        .build()))
                .build();
    }

    private static EvolutionContext defaultContext(List<EvolutionSignal> signals) {
        return new EvolutionContext(
                "test-skill",
                signals,
                "# Team Skill",
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                List.of(),
                "用户要求增加 reviewer",
                emptyTrajectory(),
                List.of(),
                Map.of()
        );
    }

    private static EvolutionSignal makeSignal(String signalType) {
        return EvolutionSignal.builder()
                .signalType(signalType)
                .section("Workflow")
                .excerpt("handoff gap")
                .skillName("test-skill")
                .context(Map.of(
                        "skill_content", "# Current content",
                        "trajectory_issues", List.of(Map.of("issue_type", "handoff"))
                ))
                .build();
    }

    private static EvolutionStore storeWithSkillAndRecord(Path tempDir, String skillName) throws Exception {
        Path skillDir = tempDir.resolve(skillName);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# Team Skill\n## Workflow\nExisting body\n");
        EvolutionStore store = new EvolutionStore(tempDir.toString());
        EvolutionRecord record = EvolutionRecord.builder()
                .id("ev_12345678")
                .source("team_skill_user_patch")
                .timestamp("2026-06-20T00:00:00Z")
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section("Collaboration")
                        .action("append")
                        .content("已有交接规则")
                        .target(EvolutionTarget.BODY)
                        .build())
                .summary("Existing summary")
                .build();
        store.saveEvolutionLog(skillName, new EvolutionLog("skill-id", "1.0.0", "2026-06-20T00:00:00Z", List.of(record)))
                .toCompletableFuture()
                .join();
        return store;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Recording model invoker.
     *
     * <p>Mirrors Python's AsyncMock LLM behavior in
     * {@code tests/unit_tests/agent_evolving/optimizer/test_team_skill_optimizer.py}.</p>
     */
    private static final class RecordingInvoker implements Model.ModelInvoker {
        private final Queue<Object> outcomes = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<ModelInvokeOptions> options = new ArrayList<>();

        private RecordingInvoker(Object... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        ) {
            this.prompts.add(extractPrompt(messages));
            this.options.add(options);
            Object outcome = this.outcomes.isEmpty() ? new AssistantMessage("") : this.outcomes.remove();
            if (outcome instanceof Throwable throwable) {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
            return CompletableFuture.completedFuture(
                    outcome instanceof AssistantMessage message ? message : new AssistantMessage(String.valueOf(outcome))
            );
        }

        private List<String> prompts() {
            return prompts;
        }

        private List<ModelInvokeOptions> options() {
            return options;
        }

        private static String extractPrompt(List<BaseMessage> messages) {
            if (messages != null && !messages.isEmpty() && messages.getFirst() instanceof UserMessage message) {
                Object content = message.getContent();
                return content == null ? "" : String.valueOf(content);
            }
            return String.valueOf(messages);
        }
    }

    /**
     * Minimal team skill operator.
     *
     * <p>Mirrors the Python operator mock used by
     * {@code tests/unit_tests/agent_evolving/optimizer/test_team_skill_optimizer.py}.</p>
     */
    private static final class TestOperator extends Operator {
        private final String operatorId;
        private Object experiences = new ArrayList<>();

        private TestOperator(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of(Protocols.EXPERIENCES_TARGET, new TunableSpec(
                    Protocols.EXPERIENCES_TARGET,
                    "list",
                    "experiences"
            ));
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of(Protocols.EXPERIENCES_TARGET, experiences);
        }

        @Override
        public void setParameter(String target, Object value) {
            if (Protocols.EXPERIENCES_TARGET.equals(target)) {
                experiences = value;
            }
        }

        @Override
        public ApplyResult applyUpdate(String target, UpdateValue update) {
            return super.applyUpdate(target, update);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            experiences = new LinkedHashMap<>(state);
        }
    }
}
