/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ConversationSignalDetector.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.signal.test_from_conv}.</p>
 */
class FromConvTest {

    @Test
    void emptyTrajectoryReturnsEmptySignals() {
        ConversationSignalDetector detector = new ConversationSignalDetector();
        Trajectory trajectory = Trajectory.builder().executionId("test").steps(List.of()).build();

        List<EvolutionSignal> signals = detector.detect(trajectory);

        assertEquals(List.of(), signals);
    }

    @Test
    void executionFailureSignal() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Run the code"),
                assistant("I'll run it", List.of(toolCall("tc_1", "bash", "{}"))),
                tool("tc_1", "bash", "Error: command failed with exit code 1")
        );
        Trajectory trajectory = buildTrajectoryFromMessages(messages);

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertEquals(1, signals.size());
        EvolutionSignal signal = signals.getFirst();
        assertEquals("execution_failure", signal.getSignalType());
        assertEquals(EvolutionCategory.SKILL_EXPERIENCE, signal.getEvolutionType());
        assertTrue(signal.getExcerpt().toLowerCase().contains("failed"));
    }

    @Test
    void userCorrectionSignal() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Use the read_file tool"),
                assistant("I'll read it", List.of(toolCall("tc_1", "read_file", "{}"))),
                tool("tc_1", "read_file", "file content"),
                message("user", "That's wrong; you should check whether the file exists first.")
        );
        Trajectory trajectory = buildTrajectoryFromMessages(messages);

        List<EvolutionSignal> correctionSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> "user_correction".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, correctionSignals.size());
        assertEquals("Examples", correctionSignals.getFirst().getSection());
    }

    @Test
    void scriptArtifactSignal() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Write a script"),
                assistant("Here's a script", List.of(toolCall("tc_1", "python_exec",
                        "{\"code\":\"print('hello world')\\nfor i in range(10): print(i)\"}"))),
                tool("tc_1", "python_exec", "hello world\n0\n1\n2\n...")
        );
        Trajectory trajectory = buildTrajectoryFromMessages(messages);

        List<EvolutionSignal> scriptSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> "script_artifact".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, scriptSignals.size());
        assertEquals("Scripts", scriptSignals.getFirst().getSection());
    }

    @Test
    void fingerprintConsistencyWithSignalDetector() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Run the code"),
                assistant("I'll run it", List.of(toolCall("tc_1", "bash", "{}"))),
                tool("tc_1", "bash", "Error: command failed")
        );
        ConversationSignalDetector detector = new ConversationSignalDetector();

        List<String> fingerprintsFromMessages = detector.detect(messages).stream()
                .map(ConversationSignalDetector::makeSignalFingerprint)
                .sorted()
                .toList();
        List<String> fingerprintsFromTrajectory = detector.detect(buildTrajectoryFromMessages(messages)).stream()
                .map(ConversationSignalDetector::makeSignalFingerprint)
                .sorted()
                .toList();

        assertEquals(fingerprintsFromMessages, fingerprintsFromTrajectory);
    }

    @Test
    void signalDeduplication() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Run multiple commands"),
                assistant("Running...", List.of(
                        toolCall("tc_1", "bash", "{}"),
                        toolCall("tc_2", "bash", "{}")
                )),
                tool("tc_1", "bash", "Error: command failed with exit code 1"),
                tool("tc_2", "bash", "Error: command failed with exit code 1")
        );

        List<EvolutionSignal> failureSignals = new ConversationSignalDetector()
                .detect(buildTrajectoryFromMessages(messages))
                .stream()
                .filter(signal -> "execution_failure".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, failureSignals.size());
    }

    @Test
    void existingSkillsFilterResolvesSkillName() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Read SKILL.md"),
                assistant("Reading...", List.of(toolCall("tc_1", "read_file", "{\"path\":\"/skills/my_skill/SKILL.md\"}"))),
                tool("tc_1", "read_file", "# My Skill\n..."),
                message("user", "That's wrong; use another approach.")
        );
        ConversationSignalDetector detector = new ConversationSignalDetector(Set.of("my_skill"));

        List<EvolutionSignal> correctionSignals = detector.detect(buildTrajectoryFromMessages(messages)).stream()
                .filter(signal -> "user_correction".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, correctionSignals.size());
        assertEquals("my_skill", correctionSignals.getFirst().getSkillName());
    }

    @Test
    void collaborationSendSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "researcher",
                "send_message",
                Map.of("to_member_name", "coder", "message", "finish data analysis"),
                "",
                Map.of()
        );

        EvolutionSignal signal = onlySignalOfType(new ConversationSignalDetector().detect(trajectory), "collaboration_send");

        assertEquals("Collaboration", signal.getSection());
        assertEquals(EvolutionCategory.SKILL_EXPERIENCE, signal.getEvolutionType());
        assertEquals("researcher", signal.getContext().get("from_member"));
        assertEquals("coder", signal.getContext().get("to_member"));
    }

    @Test
    void collaborationClaimSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory("coder", "claim_task", Map.of("task_id", "task-123"), "", Map.of());

        EvolutionSignal signal = onlySignalOfType(new ConversationSignalDetector().detect(trajectory), "collaboration_claim");

        assertEquals("Collaboration", signal.getSection());
        assertEquals("coder", signal.getContext().get("member_id"));
        assertEquals("task-123", signal.getContext().get("task_id"));
    }

    @Test
    void collaborationViewSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory("researcher", "view_task", Map.of(), "", Map.of());

        EvolutionSignal signal = onlySignalOfType(new ConversationSignalDetector().detect(trajectory), "collaboration_view");

        assertEquals("Collaboration", signal.getSection());
        assertEquals("researcher", signal.getContext().get("member_id"));
    }

    @Test
    void collaborationReceiveSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "coder",
                "write_file",
                Map.of("path", "output.py"),
                "",
                Map.of("parent_invoke_id", "invoke-researcher-001")
        );

        EvolutionSignal signal = onlySignalOfType(new ConversationSignalDetector().detect(trajectory), "collaboration_receive");

        assertEquals("Collaboration", signal.getSection());
        assertEquals("coder", signal.getContext().get("member_id"));
        assertEquals("invoke-researcher-001", signal.getContext().get("parent_invoke_id"));
    }

    @Test
    void collaborationFailureSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "researcher",
                "send_message",
                Map.of("to_member_name", "coder"),
                "Error: member coder failed to respond - timeout",
                Map.of()
        );

        EvolutionSignal signal = onlySignalOfType(new ConversationSignalDetector().detect(trajectory), "collaboration_failure");

        assertEquals("Collaboration", signal.getSection());
        assertTrue(signal.getExcerpt().toLowerCase().contains("timeout"));
    }

    @Test
    void noCollaborationSignalsForStandaloneAgent() {
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder()
                        .toolName("send_message")
                        .callArgs(Map.of("to_member_name", "other"))
                        .build())
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("standalone-exec")
                .sessionId("session-1")
                .source("standalone")
                .steps(List.of(step))
                .meta(Map.of())
                .build();

        List<EvolutionSignal> collabSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> signal.getSignalType().startsWith("collaboration_"))
                .toList();

        assertEquals(0, collabSignals.size());
    }

    @Test
    void noCollaborationSignalsForNonCollaborativeTools() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "coder",
                "bash",
                Map.of("command", "python script.py"),
                "",
                Map.of()
        );

        List<EvolutionSignal> collabSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> signal.getSignalType().startsWith("collaboration_"))
                .toList();

        assertEquals(0, collabSignals.size());
    }

    @Test
    void multipleCollaborationSignalsFromSingleTrajectory() {
        List<TrajectoryStep> steps = List.of(
                toolStep("view_task", Map.of(), "", Map.of(), 100L),
                toolStep("claim_task", Map.of("task_id", "t1"), "", Map.of("parent_invoke_id", "p1"), 200L),
                toolStep("send_message", Map.of("to_member_name", "leader"), "", Map.of(), 300L)
        );
        Trajectory trajectory = Trajectory.builder()
                .executionId("multi-collab")
                .sessionId("session-team")
                .source("online")
                .steps(steps)
                .meta(Map.of("member_id", "teammate-1", "team_id", "team-1"))
                .build();

        List<EvolutionSignal> collabSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> signal.getSignalType().startsWith("collaboration_"))
                .toList();
        Set<String> signalTypes = collabSignals.stream()
                .map(EvolutionSignal::getSignalType)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(4, collabSignals.size());
        assertTrue(signalTypes.contains("collaboration_view"));
        assertTrue(signalTypes.contains("collaboration_claim"));
        assertTrue(signalTypes.contains("collaboration_receive"));
        assertTrue(signalTypes.contains("collaboration_send"));
    }

    @Test
    void sendMessageToSelfNotCollaboration() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "researcher",
                "send_message",
                Map.of("to_member_name", "researcher"),
                "",
                Map.of()
        );

        List<EvolutionSignal> collabSendSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> "collaboration_send".equals(signal.getSignalType()))
                .toList();

        assertEquals(0, collabSendSignals.size());
    }

    private static Trajectory buildTrajectoryFromMessages(List<Map<String, Object>> messages) {
        List<TrajectoryStep> steps = new ArrayList<>();
        Map<String, Map<String, Object>> toolCallIdToResult = new LinkedHashMap<>();
        for (Map<String, Object> msg : messages) {
            if ("tool".equals(msg.get("role")) && msg.get("tool_call_id") instanceof String toolCallId) {
                toolCallIdToResult.put(toolCallId, msg);
            }
        }

        List<Map<String, Object>> llmMessages = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            String role = String.valueOf(msg.getOrDefault("role", ""));
            if (List.of("user", "assistant", "system").contains(role)) {
                llmMessages.add(msg);
                if ("assistant".equals(role) && msg.get("tool_calls") instanceof List<?> toolCalls) {
                    for (Object value : toolCalls) {
                        if (!(value instanceof Map<?, ?> rawToolCall)) {
                            continue;
                        }
                        Map<String, Object> toolCall = toStringMap(rawToolCall);
                        String toolCallId = String.valueOf(toolCall.getOrDefault("id", ""));
                        if (toolCallIdToResult.containsKey(toolCallId)) {
                            Map<String, Object> resultMsg = toolCallIdToResult.get(toolCallId);
                            steps.add(TrajectoryStep.builder()
                                    .kind("tool")
                                    .detail(ToolCallDetail.builder()
                                            .toolName(String.valueOf(toolCall.getOrDefault("name", "")))
                                            .callResult(resultMsg.getOrDefault("content", ""))
                                            .toolCallId(toolCallId)
                                            .build())
                                    .build());
                        }
                    }
                }
            }
        }

        if (!llmMessages.isEmpty()) {
            steps.add(0, TrajectoryStep.builder()
                    .kind("llm")
                    .detail(LLMCallDetail.builder()
                            .model("test-model")
                            .messages(llmMessages)
                            .build())
                    .build());
        }
        return Trajectory.builder()
                .executionId("test-exec")
                .steps(steps)
                .build();
    }

    private static Trajectory buildTeamMemberTrajectory(
            String memberId,
            String toolName,
            Map<String, Object> toolArgs,
            String toolResult,
            Map<String, Object> stepMeta
    ) {
        TrajectoryStep step = toolStep(toolName, toolArgs, toolResult, stepMeta, null);
        return Trajectory.builder()
                .executionId("exec-" + memberId)
                .sessionId("session-team")
                .source("online")
                .steps(List.of(step))
                .meta(Map.of("member_id", memberId, "team_id", "team-1"))
                .build();
    }

    private static TrajectoryStep toolStep(
            String toolName,
            Map<String, Object> toolArgs,
            String toolResult,
            Map<String, Object> stepMeta,
            Long startTimeMs
    ) {
        return TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder()
                        .toolName(toolName)
                        .callArgs(toolArgs)
                        .callResult(toolResult)
                        .build())
                .meta(stepMeta)
                .startTimeMs(startTimeMs)
                .build();
    }

    private static EvolutionSignal onlySignalOfType(List<EvolutionSignal> signals, String signalType) {
        List<EvolutionSignal> filtered = signals.stream()
                .filter(signal -> signalType.equals(signal.getSignalType()))
                .toList();
        assertEquals(1, filtered.size());
        return filtered.getFirst();
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private static Map<String, Object> assistant(String content, List<Map<String, Object>> toolCalls) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        message.put("tool_calls", toolCalls);
        return message;
    }

    private static Map<String, Object> tool(String toolCallId, String name, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "tool");
        message.put("tool_call_id", toolCallId);
        message.put("name", name);
        message.put("content", content);
        return message;
    }

    private static Map<String, Object> toolCall(String id, String name, String arguments) {
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", id);
        toolCall.put("name", name);
        toolCall.put("type", "function");
        toolCall.put("arguments", arguments);
        return toolCall;
    }

    private static Map<String, Object> toStringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
