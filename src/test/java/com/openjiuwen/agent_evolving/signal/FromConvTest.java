/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.Protocols;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ConversationSignalDetector}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/signal/test_from_conv.py}.</p>
 */
class FromConvTest {

    @Test
    void emptyTrajectoryReturnsEmptySignals() {
        ConversationSignalDetector detector = new ConversationSignalDetector();
        Trajectory trajectory = Trajectory.builder().executionId("test").steps(List.of()).build();

        assertEquals(List.of(), detector.detect(trajectory));
    }

    @Test
    void trajectoryWithMessageObjectsDoesNotRequireDictGet() {
        ConversationSignalDetector detector = new ConversationSignalDetector();
        Trajectory trajectory = Trajectory.builder()
                .executionId("message-object")
                .steps(List.of(
                        TrajectoryStep.builder()
                                .kind("llm")
                                .detail(LLMCallDetail.builder()
                                        .model("test-model")
                                        .messages(List.of(new MessageObject("system", "system prompt")))
                                        .build())
                                .build()))
                .build();

        assertEquals(List.of(), detector.detectTrajectorySignals(trajectory));
    }

    @Test
    void executionFailureSignal() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Run the code"),
                assistant("I'll run it", List.of(toolCall("tc_1", "bash", "{}"))),
                tool("tc_1", "bash", "Error: command failed with exit code 1")
        );

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(buildTrajectoryFromMessages(messages));

        assertEquals(1, signals.size());
        EvolutionSignal signal = signals.get(0);
        assertEquals("execution_failure", signal.getSignalType());
        assertTrue(signal.getExcerpt().toLowerCase().contains("failed"));
        assertEquals(Map.of("source", "passive_conversation", "tool_name", "bash"), signal.getContext());
    }

    @Test
    void scriptArtifactSignal() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Write a script"),
                assistant("Here's a script", List.of(toolCall(
                        "tc_1",
                        "python_exec",
                        "{\"code\":\"print('hello world')\\nfor i in range(10): print(i)\"}"))),
                tool("tc_1", "python_exec", "hello world\n0\n1\n2\n...")
        );

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(buildTrajectoryFromMessages(messages));
        List<EvolutionSignal> scriptSignals = signals.stream()
                .filter(signal -> "script_artifact".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, scriptSignals.size());
        assertEquals("Scripts", scriptSignals.get(0).getSection());
        assertEquals(Map.of("source", "passive_conversation", "tool_name", "python_exec"),
                scriptSignals.get(0).getContext());
    }

    @Test
    void signalDeduplication() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Run multiple commands"),
                assistant("Running...", List.of(
                        toolCall("tc_1", "bash", "{}"),
                        toolCall("tc_2", "bash", "{}"))),
                tool("tc_1", "bash", "Error: command failed with exit code 1"),
                tool("tc_2", "bash", "Error: command failed with exit code 1")
        );

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(buildTrajectoryFromMessages(messages));
        List<EvolutionSignal> failures = signals.stream()
                .filter(signal -> "execution_failure".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, failures.size());
    }

    @Test
    void fingerprintConsistencyWithSignalDetector() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Run the code"),
                assistant("I'll run it", List.of(toolCall("tc_1", "bash", "{}"))),
                tool("tc_1", "bash", "Error: command failed")
        );
        ConversationSignalDetector detector = new ConversationSignalDetector();

        List<String> messageFingerprints = detector.detect(messages).stream()
                .map(ConversationSignalDetector::makeSignalFingerprint)
                .sorted()
                .toList();
        List<String> trajectoryFingerprints = detector.detect(buildTrajectoryFromMessages(messages)).stream()
                .map(ConversationSignalDetector::makeSignalFingerprint)
                .sorted()
                .toList();

        assertEquals(messageFingerprints, trajectoryFingerprints);
    }

    @Test
    void existingSkillsFilterDoesNotEmitRuleCorrection() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Read SKILL.md"),
                assistant("Reading...", List.of(toolCall(
                        "tc_1",
                        "read_file",
                        "{\"path\":\"/skills/my_skill/SKILL.md\"}"))),
                tool("tc_1", "read_file", "file content"),
                message("user", "That is wrong; use another method.")
        );

        List<EvolutionSignal> signals = new ConversationSignalDetector(Set.of("my_skill"))
                .detect(buildTrajectoryFromMessages(messages));
        List<EvolutionSignal> correctionSignals = signals.stream()
                .filter(signal -> "user_correction".equals(signal.getSignalType()))
                .toList();

        assertEquals(0, correctionSignals.size());
    }

    @Test
    void detectUserIntentUsesLlmJudgment() {
        List<Map<String, Object>> messages = List.of(
                message("user", "Use the read_file tool"),
                assistant("I'll read the file", List.of(toolCall(
                        "tc_1",
                        "read_file",
                        "{\"path\":\"/skills/my_skill/SKILL.md\"}"))),
                tool("tc_1", "read_file", "file content"),
                message("user", "That is wrong; you should check whether the file exists first.")
        );

        RecordingLlmInvoker llm = new RecordingLlmInvoker(
                Map.of("content", "{\"is_feedback\":true,\"excerpt\":\"check whether the file exists first\"}"));
        ConversationSignalDetector detector = new ConversationSignalDetector(Set.of("my_skill"))
                .bindLlm(llm, "test-model");

        List<EvolutionSignal> signals = detector.detectUserIntent(buildTrajectoryFromMessages(messages))
                .toCompletableFuture()
                .join();

        assertEquals(1, signals.size());
        assertEquals(Protocols.USER_INTENT_SIGNAL, signals.get(0).getSignalType());
        assertEquals("my_skill", signals.get(0).getSkillName());
        assertEquals(Map.of("source", "passive_conversation"), signals.get(0).getContext());
        assertEquals("test-model", llm.model);
        assertEquals(30, llm.timeoutSeconds);
    }

    @Test
    void detectUserIntentWithoutBoundLlmUsesRuleFallback() {
        List<Map<String, Object>> messages = List.of(
                assistant("", List.of(Map.of("arguments", "/skills/my_skill/SKILL.md"))),
                message("user", "That is wrong; check whether the file exists first.")
        );

        List<EvolutionSignal> signals = new ConversationSignalDetector(Set.of("my_skill"))
                .detectUserIntent(messages)
                .toCompletableFuture()
                .join();

        assertEquals(1, signals.size());
        assertEquals(Protocols.USER_INTENT_SIGNAL, signals.get(0).getSignalType());
        assertEquals("That is wrong; check whether the file exists first.", signals.get(0).getExcerpt());
    }

    @Test
    void detectUserMessageFeedbackKeepsLegacyUserCorrectionType() {
        List<Map<String, Object>> messages = List.of(
                assistant("", List.of(Map.of("arguments", "/skills/my_skill/SKILL.md"))),
                message("user", "That is wrong; check whether the file exists first.")
        );

        List<EvolutionSignal> signals = new ConversationSignalDetector(Set.of("my_skill"))
                .detectUserMessageFeedback(messages)
                .toCompletableFuture()
                .join();

        assertEquals(1, signals.size());
        assertEquals("user_correction", signals.get(0).getSignalType());
        assertEquals("Examples", signals.get(0).getSection());
    }

    @Test
    void detectUserIntentInvalidJsonUsesRuleFallback() {
        RecordingLlmInvoker llm = new RecordingLlmInvoker(Map.of("content", "not-json"));
        ConversationSignalDetector detector = new ConversationSignalDetector(Set.of("my_skill"))
                .bindLlm(llm, "test-model");
        List<Map<String, Object>> messages = List.of(
                assistant("", List.of(Map.of("arguments", "/skills/my_skill/SKILL.md"))),
                message("user", "That is wrong; check whether the file exists first.")
        );

        List<EvolutionSignal> signals = detector.detectUserIntent(messages).toCompletableFuture().join();

        assertEquals(1, signals.size());
        assertEquals(Protocols.USER_INTENT_SIGNAL, signals.get(0).getSignalType());
        assertEquals("That is wrong; check whether the file exists first.", signals.get(0).getExcerpt());
    }

    @Test
    void detectUserIntentNonObjectJsonFallsBackToRule() {
        RecordingLlmInvoker llm = new RecordingLlmInvoker(Map.of("content", "[\"not-an-object\"]"));
        ConversationSignalDetector detector = new ConversationSignalDetector(Set.of("my_skill"))
                .bindLlm(llm, "test-model");
        List<Map<String, Object>> messages = List.of(
                assistant("", List.of(Map.of("arguments", "/skills/my_skill/SKILL.md"))),
                message("user", "That is wrong; check whether the file exists first.")
        );

        List<EvolutionSignal> signals = detector.detectUserIntent(messages).toCompletableFuture().join();

        assertEquals(1, signals.size());
        assertEquals(Protocols.USER_INTENT_SIGNAL, signals.get(0).getSignalType());
        assertEquals("That is wrong; check whether the file exists first.", signals.get(0).getExcerpt());
    }

    @Test
    void detectUserIntentReturnsEmptyWhenLlmFailsAndRuleDoesNotMatch() {
        ConversationSignalDetector detector = new ConversationSignalDetector(Set.of("my_skill"))
                .bindLlm((model, invokeMessages, timeoutSeconds) ->
                                CompletableFuture.failedFuture(new IllegalStateException("llm down")),
                        "test-model");
        List<Map<String, Object>> messages = List.of(
                assistant("", List.of(Map.of("arguments", "/skills/my_skill/SKILL.md"))),
                message("user", "Hello")
        );

        List<EvolutionSignal> signals = detector.detectUserIntent(messages).toCompletableFuture().join();

        assertEquals(List.of(), signals);
    }

    @Test
    void detectSkillFromToolCallsIgnoresNonReadToolsWithSkillPath() {
        ConversationSignalDetector detector = new ConversationSignalDetector(Set.of("my_skill"));
        List<Map<String, Object>> messages = List.of(
                assistant("", List.of(Map.of(
                        "name", "bash",
                        "arguments", "{\"command\":\"cat /skills/my_skill/SKILL.md\"}"))),
                message("user", "That is wrong; check whether the file exists first.")
        );

        List<EvolutionSignal> signals = detector.detectUserIntent(messages).toCompletableFuture().join();

        assertEquals(List.of(), signals);
    }

    @Test
    void collaborationDetectionHandlesMessageObjects() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("message-object-team-member")
                .sessionId("session-team")
                .source("online")
                .steps(List.of(
                        TrajectoryStep.builder()
                                .kind("llm")
                                .detail(LLMCallDetail.builder()
                                        .model("test-model")
                                        .messages(List.of(new MessageObject("system", "system prompt")))
                                        .build())
                                .build(),
                        TrajectoryStep.builder()
                                .kind("tool")
                                .detail(ToolCallDetail.builder()
                                        .toolName("send_message")
                                        .callArgs(Map.of("to_member_name", "coder"))
                                        .build())
                                .build()))
                .meta(Map.of("member_id", "researcher", "team_id", "team-1"))
                .build();

        List<EvolutionSignal> signals = new ConversationSignalDetector().detectTrajectorySignals(
                trajectory,
                List.of(message("system", "system prompt")));

        assertEquals(List.of("collaboration_send"), signalTypes(signals));
    }

    @Test
    void detectTrajectorySignalsUsesProvidedMessagesAndCollaborationSignals() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "researcher",
                "send_message",
                Map.of("to_member_name", "coder"),
                "",
                Map.of());

        List<EvolutionSignal> signals = new ConversationSignalDetector().detectTrajectorySignals(
                trajectory,
                List.of(message("system", "system prompt")));

        assertEquals(1, signals.size());
        assertEquals("collaboration_send", signals.get(0).getSignalType());
        assertEquals("coder", signals.get(0).getContext().get("to_member"));
    }

    @Test
    void collaborationClaimSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "coder",
                "claim_task",
                Map.of("task_id", "task-123"),
                "",
                Map.of());

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertEquals(1, signals.size());
        EvolutionSignal signal = signals.get(0);
        assertEquals("collaboration_claim", signal.getSignalType());
        assertEquals("Collaboration", signal.getSection());
        assertEquals("passive_collaboration", signal.getContext().get("source"));
        assertEquals("claim_task", signal.getContext().get("tool_name"));
        assertEquals("coder", signal.getContext().get("member_id"));
        assertEquals("task-123", signal.getContext().get("task_id"));
    }

    @Test
    void collaborationViewSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "researcher",
                "view_task",
                Map.of(),
                "",
                Map.of());

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertEquals(1, signals.size());
        EvolutionSignal signal = signals.get(0);
        assertEquals("collaboration_view", signal.getSignalType());
        assertEquals("Collaboration", signal.getSection());
        assertEquals("passive_collaboration", signal.getContext().get("source"));
        assertEquals("view_task", signal.getContext().get("tool_name"));
        assertEquals("researcher", signal.getContext().get("member_id"));
    }

    @Test
    void collaborationReceiveSignal() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "coder",
                "write_file",
                Map.of("path", "output.py"),
                "",
                Map.of("parent_invoke_id", "invoke-researcher-001"));

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertEquals(1, signals.size());
        EvolutionSignal signal = signals.get(0);
        assertEquals("collaboration_receive", signal.getSignalType());
        assertEquals("Collaboration", signal.getSection());
        assertEquals("passive_collaboration", signal.getContext().get("source"));
        assertEquals("write_file", signal.getContext().get("tool_name"));
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
                Map.of());

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);
        List<EvolutionSignal> failureSignals = signals.stream()
                .filter(signal -> "collaboration_failure".equals(signal.getSignalType()))
                .toList();

        assertEquals(1, failureSignals.size());
        EvolutionSignal signal = failureSignals.get(0);
        assertEquals("Collaboration", signal.getSection());
        assertTrue(signal.getExcerpt().toLowerCase().contains("timeout"));
        assertEquals("passive_collaboration", signal.getContext().get("source"));
        assertEquals("send_message", signal.getContext().get("tool_name"));
    }

    @Test
    void noCollaborationSignalsForStandaloneAgent() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("standalone-exec")
                .sessionId("session-1")
                .source("standalone")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("tool")
                        .detail(ToolCallDetail.builder()
                                .toolName("send_message")
                                .callArgs(Map.of("to_member_name", "other"))
                                .build())
                        .build()))
                .meta(Map.of())
                .build();

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertFalse(signals.stream().anyMatch(signal -> signal.getSignalType().startsWith("collaboration_")));
    }

    @Test
    void noCollaborationSignalsForNonCollaborativeTools() {
        Trajectory trajectory = buildTeamMemberTrajectory(
                "coder",
                "bash",
                Map.of("command", "python script.py"),
                "",
                Map.of());

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertFalse(signals.stream().anyMatch(signal -> signal.getSignalType().startsWith("collaboration_")));
    }

    @Test
    void multipleCollaborationSignalsFromSingleTrajectory() {
        List<TrajectoryStep> steps = List.of(
                TrajectoryStep.builder()
                        .kind("tool")
                        .detail(ToolCallDetail.builder()
                                .toolName("view_task")
                                .callArgs(Map.of())
                                .build())
                        .startTimeMs(100L)
                        .build(),
                TrajectoryStep.builder()
                        .kind("tool")
                        .detail(ToolCallDetail.builder()
                                .toolName("claim_task")
                                .callArgs(Map.of("task_id", "t1"))
                                .build())
                        .meta(Map.of("parent_invoke_id", "p1"))
                        .startTimeMs(200L)
                        .build(),
                TrajectoryStep.builder()
                        .kind("tool")
                        .detail(ToolCallDetail.builder()
                                .toolName("send_message")
                                .callArgs(Map.of("to_member_name", "leader"))
                                .build())
                        .startTimeMs(300L)
                        .build());
        Trajectory trajectory = Trajectory.builder()
                .executionId("multi-collab")
                .sessionId("session-team")
                .source("online")
                .steps(steps)
                .meta(Map.of("member_id", "teammate-1", "team_id", "team-1"))
                .build();

        List<EvolutionSignal> collaborationSignals = new ConversationSignalDetector().detect(trajectory).stream()
                .filter(signal -> signal.getSignalType().startsWith("collaboration_"))
                .toList();
        Set<String> signalTypes = collaborationSignals.stream()
                .map(EvolutionSignal::getSignalType)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(4, collaborationSignals.size());
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
                Map.of());

        List<EvolutionSignal> signals = new ConversationSignalDetector().detect(trajectory);

        assertEquals(0, signals.stream()
                .filter(signal -> "collaboration_send".equals(signal.getSignalType()))
                .count());
    }

    @Test
    void signalDetectorAliasDelegates() {
        SignalDetector detector = new SignalDetector();

        assertInstanceOf(ConversationSignalDetector.class, detector);
        assertEquals(List.of(), detector.detect(List.of()));
    }

    private static Trajectory buildTrajectoryFromMessages(List<Map<String, Object>> messages) {
        List<TrajectoryStep> steps = new ArrayList<>();
        Map<String, Map<String, Object>> toolCallIdToResult = new LinkedHashMap<>();
        for (Map<String, Object> message : messages) {
            if ("tool".equals(message.get("role")) && message.get("tool_call_id") instanceof String toolCallId) {
                toolCallIdToResult.put(toolCallId, message);
            }
        }

        List<Object> llmMessages = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.getOrDefault("role", ""));
            if (List.of("user", "assistant", "system").contains(role)) {
                llmMessages.add(message);
                if ("assistant".equals(role) && message.get("tool_calls") instanceof List<?> toolCalls) {
                    for (Object value : toolCalls) {
                        if (!(value instanceof Map<?, ?> rawToolCall)) {
                            continue;
                        }
                        Map<String, Object> toolCall = toStringMap(rawToolCall);
                        String toolCallId = String.valueOf(toolCall.getOrDefault("id", ""));
                        if (toolCallIdToResult.containsKey(toolCallId)) {
                            Map<String, Object> resultMessage = toolCallIdToResult.get(toolCallId);
                            steps.add(TrajectoryStep.builder()
                                    .kind("tool")
                                    .detail(ToolCallDetail.builder()
                                            .toolName(String.valueOf(toolCall.getOrDefault("name", "")))
                                            .callResult(resultMessage.getOrDefault("content", ""))
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
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder()
                        .toolName(toolName)
                        .callArgs(toolArgs)
                        .callResult(toolResult)
                        .build())
                .meta(stepMeta)
                .build();
        return Trajectory.builder()
                .executionId("exec-" + memberId)
                .sessionId("session-team")
                .source("online")
                .steps(List.of(step))
                .meta(Map.of("member_id", memberId, "team_id", "team-1"))
                .build();
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

    private static List<String> signalTypes(List<EvolutionSignal> signals) {
        return signals.stream().map(EvolutionSignal::getSignalType).toList();
    }

    private static final class MessageObject {
        private final String role;
        private final String content;

        private MessageObject(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    private static final class RecordingLlmInvoker implements ConversationSignalDetector.LlmInvoker {
        private final Object response;
        private String model;
        private int timeoutSeconds;

        private RecordingLlmInvoker(Object response) {
            this.response = response;
        }

        @Override
        public CompletableFuture<Object> invoke(String model, List<Map<String, Object>> messages, int timeoutSeconds) {
            this.model = model;
            this.timeoutSeconds = timeoutSeconds;
            return CompletableFuture.completedFuture(response);
        }
    }
}
