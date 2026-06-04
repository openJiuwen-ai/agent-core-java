/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests for TeamSkillOptimizer prompt templates and patch generation.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.test_team_skill_optimizer}.</p>
 */
class TeamSkillOptimizerTest {

    private static final LlmResilience.LLMInvokePolicy FAST_POLICY =
            new LlmResilience.LLMInvokePolicy(5, 20, 3, 0.0, true);

    @Test
    void testUserPatchPromptCnExists() {
        assertTrue(TeamSkillOptimizer.USER_PATCH_PROMPTS.containsKey("cn"));
        assertTrue(TeamSkillOptimizer.USER_PATCH_PROMPTS.get("cn").contains("用户意见"));
    }

    @Test
    void testUserPatchPromptEnExists() {
        assertTrue(TeamSkillOptimizer.USER_PATCH_PROMPTS.containsKey("en"));
        assertTrue(TeamSkillOptimizer.USER_PATCH_PROMPTS.get("en").contains("User suggestion"));
    }

    @Test
    void testTrajectoryPatchPromptCnExists() {
        assertTrue(TeamSkillOptimizer.TRAJECTORY_PATCH_PROMPTS.containsKey("cn"));
        assertTrue(TeamSkillOptimizer.TRAJECTORY_PATCH_PROMPTS.get("cn").contains("执行轨迹"));
    }

    @Test
    void testTrajectoryPatchPromptEnExists() {
        assertTrue(TeamSkillOptimizer.TRAJECTORY_PATCH_PROMPTS.containsKey("en"));
        assertTrue(TeamSkillOptimizer.TRAJECTORY_PATCH_PROMPTS.get("en").contains("Detected issues"));
    }

    @Test
    void testPatchLlmPolicyPropertyReturnsConfiguredPolicy() {
        LlmResilience.LLMInvokePolicy policy = new LlmResilience.LLMInvokePolicy(15, 45, 2, 0.0, true);
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(mock(Model.class), "test-model", "en", null, policy);

        assertSame(policy, optimizer.getPatchLlmPolicy());
    }

    @Test
    void testUpdateLlmUpdatesRuntimeReferences() {
        Model oldLlm = mock(Model.class);
        Model newLlm = mock(Model.class);
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(oldLlm, "m1", "cn", null, FAST_POLICY);

        optimizer.updateLlm(newLlm, "m2");

        assertSame(newLlm, optimizer.getLlm());
        assertEquals("m2", optimizer.getModel());
    }

    @Test
    void testGenerateUserPatchReturnsRecordOnValidResponse() throws Exception {
        List<String> prompts = new ArrayList<>();
        List<Float> timeouts = new ArrayList<>();
        Model llm = llmWith(List.of("""
                {"section":"Collaboration","action":"append","content":"Role A notifies Role B after completion."}
                """), prompts, timeouts);
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en");

        Optional<EvolutionRecord> result =
                optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "Improve handoff").join();

        EvolutionRecord record = result.orElseThrow();
        assertEquals("team_skill_user_patch", record.getSource());
        assertEquals("Collaboration", record.getChange().getSection());
        assertEquals("Role A notifies Role B after completion.", record.getChange().getContent());
        assertEquals(EvolutionTarget.BODY, record.getChange().getTarget());
        assertEquals(120f, timeouts.get(0));
        assertTrue(prompts.get(0).contains("Improve handoff"));
    }

    @Test
    void testGenerateUserPatchRaisesOnEmptyResponse() throws Exception {
        Model llm = llmWith(List.of(""), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Throwable cause = assertJoinCause(BaseError.class,
                () -> optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "intent").join());

        assertInstanceOf(BaseError.class, cause);
    }

    @Test
    void testGenerateUserPatchRaisesOnInvalidJson() throws Exception {
        Model llm = llmWith(List.of("not json at all"), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "cn", null, FAST_POLICY);

        Throwable cause = assertJoinCause(BaseError.class,
                () -> optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "intent").join());

        assertInstanceOf(BaseError.class, cause);
    }

    @Test
    void testGenerateUserPatchRaisesOnEmptyContent() throws Exception {
        Model llm = llmWith(List.of("""
                {"section":"Instructions","action":"append","content":"   "}
                """), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Throwable cause = assertJoinCause(IllegalArgumentException.class,
                () -> optimizer.generateUserPatch(emptyTrajectory(), "test-skill", "intent").join());

        assertTrue(cause.getMessage().contains("empty content"));
    }

    @Test
    void testGenerateUserPatchRetriesWhenFirstResponseIsInvalidJson() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                "not json",
                "{\"section\":\"Collaboration\",\"action\":\"append\",\"content\":\"Share context before assigning roles.\"}"
        ), prompts, new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        EvolutionRecord record = optimizer.generateUserPatch(
                emptyTrajectory(), "test-skill", "Optimize collaboration").join().orElseThrow();

        assertEquals("Collaboration", record.getChange().getSection());
        assertEquals(2, prompts.size());
    }

    @Test
    void testGenerateUserPatchRetriesWithShorterPromptAfterTimeout() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                new TimeoutException("request timed out"),
                "{\"section\":\"Collaboration\",\"action\":\"append\",\"content\":\"Shorter handoff instructions.\"}"
        ), prompts, new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        EvolutionRecord record = optimizer.generateUserPatch(
                longTrajectory(), "test-skill", "Improve collaboration ".repeat(400)).join().orElseThrow();

        assertEquals("Collaboration", record.getChange().getSection());
        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).length() < prompts.get(0).length());
    }

    @Test
    void testGenerateTrajectoryPatchReturnsRecordWhenNeedPatchTrue() throws Exception {
        List<String> prompts = new ArrayList<>();
        List<Float> timeouts = new ArrayList<>();
        Model llm = llmWith(List.of("""
                {"need_patch":true,"section":"Constraints","content":"Do not exceed a 30-second handoff window.",
                 "reason":"Repeated timeouts were observed."}
                """), prompts, timeouts);
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en");

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                emptyTrajectory(),
                "test-skill",
                "# Team Skill\n## Workflow\nKeep reviewer handoff explicit.",
                List.of(Map.of("issue_type", "timeout", "severity", "high"))).join().orElseThrow();

        assertEquals("team_skill_trajectory_patch", record.getSource());
        assertEquals("Constraints", record.getChange().getSection());
        assertTrue(record.getChange().getContent().contains("30-second"));
        assertEquals(120f, timeouts.get(0));
        assertTrue(prompts.get(0).contains("# Team Skill"));
    }

    @Test
    void testGenerateTrajectoryPatchUsesCustomPatchLlmPolicy() throws Exception {
        List<Float> timeouts = new ArrayList<>();
        Model llm = llmWith(List.of("""
                {"need_patch":true,"section":"Workflow","content":"Coordinate reviewer handoff.",
                 "reason":"Context loss."}
                """), new ArrayList<>(), timeouts);
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(
                llm,
                "test-model",
                "en",
                null,
                new LlmResilience.LLMInvokePolicy(15, 45, 2, 0.0, true));

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                emptyTrajectory(), "test-skill", "# Skill", List.of(Map.of("issue_type", "handoff")))
                .join().orElseThrow();

        assertEquals("Workflow", record.getChange().getSection());
        assertEquals(15f, timeouts.get(0));
    }

    @Test
    void testGenerateTrajectoryPatchReturnsNoneWhenNeedPatchFalse() throws Exception {
        Model llm = llmWith(List.of("""
                {"need_patch":false,"reason":"normal execution"}
                """), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Optional<EvolutionRecord> record = optimizer.generateTrajectoryPatch(
                emptyTrajectory(), "test-skill", "# Skill", List.of()).join();

        assertTrue(record.isEmpty());
    }

    @Test
    void testGenerateTrajectoryPatchRaisesOnEmptyResponse() throws Exception {
        Model llm = llmWith(List.of(""), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        assertJoinCause(BaseError.class,
                () -> optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", "# Skill", List.of()).join());
    }

    @Test
    void testGenerateTrajectoryPatchRaisesOnInvalidJson() throws Exception {
        Model llm = llmWith(List.of("broken json"), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        assertJoinCause(BaseError.class,
                () -> optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", "# Skill", List.of()).join());
    }

    @Test
    void testGenerateTrajectoryPatchRaisesOnEmptyContent() throws Exception {
        Model llm = llmWith(List.of("""
                {"need_patch":true,"section":"Workflow","content":"   "}
                """), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Throwable cause = assertJoinCause(IllegalArgumentException.class,
                () -> optimizer.generateTrajectoryPatch(emptyTrajectory(), "test-skill", "# Skill", List.of()).join());

        assertTrue(cause.getMessage().contains("empty content"));
    }

    @Test
    void testGenerateTrajectoryPatchRetriesWithShorterPromptAfterTimeout() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                new TimeoutException("request timed out"),
                "{\"need_patch\":true,\"section\":\"Workflow\",\"content\":\"Summarize before retry.\","
                        + "\"reason\":\"long trajectory\"}"
        ), prompts, new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        EvolutionRecord record = optimizer.generateTrajectoryPatch(
                longTrajectory(),
                "test-skill",
                "# Skill\n" + "content\n".repeat(4000),
                List.of(Map.of("issue_type", "timeout", "description", "slow ".repeat(1000))))
                .join().orElseThrow();

        assertEquals("Workflow", record.getChange().getSection());
        assertTrue(prompts.get(1).length() < prompts.get(0).length());
    }

    @Test
    void testGeneratePatchRetriesWithShorterPromptAfterTimeout() throws Exception {
        List<String> prompts = new ArrayList<>();
        Model llm = llmWith(List.of(
                new TimeoutException("request timed out"),
                "{\"need_patch\":true,\"section\":\"Constraints\",\"content\":\"Limit context summary length.\","
                        + "\"reason\":\"prompt too long\"}"
        ), prompts, new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        EvolutionRecord record = optimizer.generatePatch(
                longTrajectory(), "test-skill", "# Skill\n" + "content\n".repeat(4000)).join().orElseThrow();

        assertEquals("team_skill_evolution", record.getSource());
        assertEquals("Constraints", record.getChange().getSection());
        assertTrue(prompts.get(1).length() < prompts.get(0).length());
    }

    @Test
    void testGeneratePatchReturnsNoneWhenNeedPatchFalse() throws Exception {
        Model llm = llmWith(List.of("""
                {"need_patch":false,"reason":"no new insight"}
                """), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Optional<EvolutionRecord> record = optimizer.generatePatch(emptyTrajectory(), "test-skill", "# Skill").join();

        assertTrue(record.isEmpty());
    }

    @Test
    void testParseJsonHandlesCodeFenceCommentsTrailingCommas() {
        Map<String, Object> parsed = TeamSkillOptimizer.parseJson("""
                ```json
                {
                  // comment
                  "need_patch": true,
                  "section": "Workflow",
                }
                ```
                """);

        assertNotNull(parsed);
        assertEquals(Boolean.TRUE, parsed.get("need_patch"));
        assertEquals("Workflow", parsed.get("section"));
    }

    @Test
    void testParseJsonUsesBalancedObjectWhenMarkdownContainsBraces() {
        Map<String, Object> parsed = TeamSkillOptimizer.parseJson("""
                prefix
                {"section":"Examples","content":"Use code fences like ```json {not real json}``` safely"}
                suffix
                """);

        assertNotNull(parsed);
        assertEquals("Examples", parsed.get("section"));
        assertTrue(String.valueOf(parsed.get("content")).contains("{not real json}"));
    }

    @Test
    void testBuildTrajectorySummaryPrioritizesToolAndLlmSteps() {
        Trajectory trajectory = Trajectory.builder()
                .steps(List.of(
                        TrajectoryStep.builder()
                                .kind("tool")
                                .operatorId("spawn_member")
                                .detail(Map.of(
                                        "tool_name", "spawn_member",
                                        "call_args", "role_reviewer task",
                                        "call_result", "created reviewer"))
                                .build(),
                        TrajectoryStep.builder()
                                .kind("llm")
                                .detail(Map.of("response", "The leader assigned review work."))
                                .build()))
                .build();

        String summary = TeamSkillOptimizer.buildTrajectorySummary(trajectory);

        assertTrue(summary.contains("### Tool Calls (1)"));
        assertTrue(summary.contains("[Tool:spawn_member]"));
        assertTrue(summary.contains("### LLM Responses (1)"));
        assertTrue(summary.contains("The leader assigned review work."));
    }

    @Test
    void testBuildTrajectorySummaryTruncatesToolSection() {
        List<TrajectoryStep> steps = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            steps.add(TrajectoryStep.builder()
                    .kind("tool")
                    .operatorId("send_message")
                    .detail(Map.of(
                            "tool_name", "send_message",
                            "call_args", "a".repeat(1000),
                            "call_result", "b".repeat(1000)))
                    .build());
        }

        String summary = TeamSkillOptimizer.buildTrajectorySummary(Trajectory.builder().steps(steps).build());

        assertTrue(summary.contains("tool section truncated"));
        assertTrue(summary.length() < 32_000);
    }

    @Test
    void testRegenerateBodyReturnsBodyWhenLlmProducesMarkdown() throws Exception {
        Model llm = llmWith(List.of("# Improved\n\n" + "body ".repeat(20)), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Workflow")
                .action("append")
                .content("Add review handoff.")
                .target(EvolutionTarget.BODY)
                .build();
        EvolutionRecord record = EvolutionRecord.make("source", "ctx", patch, 0.6, null);

        Optional<String> body = optimizer.regenerateBody("test-skill", "# Current", List.of(record), null).join();

        assertTrue(body.isPresent());
        assertTrue(body.get().contains("# Improved"));
    }

    @Test
    void testProposeNewSkillReturnsParsedProposal() throws Exception {
        Model llm = llmWith(List.of("""
                {"should_create":true,"name":"review-workflow","description":"review flow","body":"body"}
                """), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Optional<Map<String, Object>> proposal = optimizer.proposeNewSkill(emptyTrajectory(), "existing").join();

        assertTrue(proposal.isPresent());
        assertEquals("review-workflow", proposal.get().get("name"));
    }

    @Test
    void testProposeNewSkillReturnsEmptyWhenDeclined() throws Exception {
        Model llm = llmWith(List.of("""
                {"should_create":false,"reason":"covered"}
                """), new ArrayList<>(), new ArrayList<>());
        TeamSkillOptimizer optimizer = new TeamSkillOptimizer(llm, "test-model", "en", null, FAST_POLICY);

        Optional<Map<String, Object>> proposal = optimizer.proposeNewSkill(emptyTrajectory(), "existing").join();

        assertFalse(proposal.isPresent());
    }

    private static Trajectory emptyTrajectory() {
        return Trajectory.builder().steps(List.of()).build();
    }

    private static Trajectory longTrajectory() {
        return Trajectory.builder()
                .steps(List.of(
                        TrajectoryStep.builder()
                                .kind("tool")
                                .operatorId("spawn_member")
                                .detail(Map.of(
                                        "tool_name", "spawn_member",
                                        "call_args", "role_researcher ".repeat(1000),
                                        "call_result", "result ".repeat(1000)))
                                .build(),
                        TrajectoryStep.builder()
                                .kind("tool")
                                .operatorId("send_message")
                                .detail(Map.of(
                                        "tool_name", "send_message",
                                        "call_args", "context ".repeat(1000),
                                        "call_result", "sent ".repeat(1000)))
                                .build()))
                .build();
    }

    private static Throwable assertJoinCause(Class<?> expectedType, Runnable runnable) {
        CompletionException error = assertThrows(CompletionException.class, runnable::run);
        Throwable cause = error.getCause();
        assertInstanceOf(expectedType, cause);
        return cause;
    }

    private static Model llmWith(List<Object> results, List<String> prompts, List<Float> timeouts) throws Exception {
        Model llm = mock(Model.class);
        final int[] index = {0};
        Answer<AssistantMessage> answer = invocation -> {
            Object messages = invocation.getArgument(0);
            prompts.add(extractPrompt(messages));
            timeouts.add(invocation.getArgument(8));
            Object result = results.get(Math.min(index[0], results.size() - 1));
            index[0]++;
            if (result instanceof Exception exception) {
                throw exception;
            }
            if (result instanceof AssistantMessage message) {
                return message;
            }
            return new AssistantMessage(String.valueOf(result));
        };
        doAnswer(answer).when(llm).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return llm;
    }

    private static String extractPrompt(Object messages) {
        if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof UserMessage message) {
            Object content = message.getContent();
            return content != null ? String.valueOf(content) : "";
        }
        return String.valueOf(messages);
    }
}
