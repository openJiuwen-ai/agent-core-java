/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.config.WorkflowAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentConcurrent} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_concurrent.py}.
 */
public class WorkflowAgentConcurrentMissingTest {

    @Test
    void testConcurrentConversations() {
        DeterministicWorkflowController controller = new DeterministicWorkflowController();
        WorkflowAgent agent = agentWith(controller);
        List<String> conversationIds = List.of(unique(), unique(), unique());

        List<CompletableFuture<Map.Entry<String, Object>>> futures = conversationIds.stream()
                .map(conversationId -> CompletableFuture.supplyAsync(() -> Map.entry(
                        conversationId,
                        agent.invoke(query("check weather", conversationId), new RecordingSession(conversationId))
                                .toCompletableFuture()
                                .join()
                )))
                .toList();

        int successCount = 0;
        for (CompletableFuture<Map.Entry<String, Object>> future : futures) {
            Map.Entry<String, Object> item = future.join();
            OutputSchema interaction = firstInteraction(item.getValue());
            assertThat(interaction.getType()).isEqualTo(Constant.INTERACTION);
            InteractionOutput payload = (InteractionOutput) interaction.getPayload();
            assertThat(payload.getId()).isEqualTo("questioner");
            assertThat(controller.roles(item.getKey())).containsExactly("user", "assistant");
            successCount++;
        }

        assertThat(successCount).isEqualTo(3);
    }

    @Test
    void testRealtimeInterruptCancellation() {
        DeterministicWorkflowController controller = new DeterministicWorkflowController();
        WorkflowAgent agent = agentWith(controller);
        RecordingSession session = new RecordingSession("test-realtime-interrupt");

        List<Object> phase1 = drain(agent.stream(query("check weather", session.getSessionId()), session));
        assertThat(phase1).isEmpty();
        assertThat(controller.slowWorkflowStarted(session.getSessionId())).isTrue();

        List<Object> phase2 = drain(agent.stream(query("check stock", session.getSessionId()), session));
        assertThat(controller.wasCancelled(session.getSessionId())).isTrue();
        assertThat(phase2).hasSize(1);
        assertThat(((OutputSchema) phase2.getFirst()).getType()).isEqualTo(Constant.INTERACTION);

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("questioner", "AAPL");
        List<Object> phase3 = drain(agent.stream(query(interactiveInput, session.getSessionId()), session));

        OutputSchema finalChunk = onlyChunk(phase3);
        assertThat(finalChunk.getType()).isEqualTo("workflow_final");
        assertThat(map(finalChunk.getPayload()).get("response")).asString().contains("AAPL");
        assertThat(controller.roles(session.getSessionId()))
                .containsExactly("user", "user", "assistant", "assistant", "user", "assistant");
    }

    @Test
    void testComponentStateReset() {
        DeterministicWorkflowController controller = new DeterministicWorkflowController();
        WorkflowAgent agent = agentWith(controller);
        RecordingSession session = new RecordingSession("test-state-reset");

        Object first = agent.invoke(query("collect info", session.getSessionId()), session)
                .toCompletableFuture()
                .join();
        assertThat(firstInteraction(first).getType()).isEqualTo(Constant.INTERACTION);

        InteractiveInput interactiveOne = new InteractiveInput();
        interactiveOne.update("questioner", "shanghai");
        Map<String, Object> second = map(agent.invoke(query(interactiveOne, session.getSessionId()), session)
                .toCompletableFuture()
                .join());
        assertThat(second).containsEntry("result_type", "answer");
        assertThat(map(second.get("output")).get("response")).asString().contains("shanghai");

        Object third = agent.invoke(query("collect info again", session.getSessionId()), session)
                .toCompletableFuture()
                .join();
        assertThat(firstInteraction(third).getType()).isEqualTo(Constant.INTERACTION);

        InteractiveInput interactiveTwo = new InteractiveInput();
        interactiveTwo.update("questioner", "beijing");
        Map<String, Object> fourth = map(agent.invoke(query(interactiveTwo, session.getSessionId()), session)
                .toCompletableFuture()
                .join());
        String response = String.valueOf(map(fourth.get("output")).get("response"));
        assertThat(response).contains("beijing");
        assertThat(response).doesNotContain("shanghai");
        assertThat(controller.roles(session.getSessionId()))
                .containsExactly("user", "assistant", "user", "assistant",
                        "user", "assistant", "user", "assistant");
    }

    private static WorkflowAgent agentWith(DeterministicWorkflowController controller) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId("workflow-concurrent-missing-test");
        config.setVersion("1.0");
        config.setDescription("workflow concurrent missing-test parity");
        config.setWorkflows(List.of());
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(controller);
        return agent;
    }

    private static Map<String, Object> query(Object query, String conversationId) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        inputs.put("conversation_id", conversationId);
        return inputs;
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }

    private static OutputSchema firstInteraction(Object result) {
        List<Object> items = list(result);
        assertThat(items).isNotEmpty();
        assertThat(items.getFirst()).isInstanceOf(OutputSchema.class);
        return (OutputSchema) items.getFirst();
    }

    private static OutputSchema onlyChunk(List<Object> chunks) {
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).isInstanceOf(OutputSchema.class);
        return (OutputSchema) chunks.getFirst();
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    public static final class DeterministicWorkflowController {
        private final ConcurrentMap<String, List<String>> chatRoles = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, String> pendingQuestioner = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Boolean> slowWorkflowBySession = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Boolean> cancelledBySession = new ConcurrentHashMap<>();

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            Object query = inputs.get("query");
            String sessionId = session.getSessionId();
            if (query instanceof InteractiveInput input) {
                return CompletableFuture.completedFuture(answer(sessionId, input));
            }
            appendRole(sessionId, "user");
            appendRole(sessionId, "assistant");
            pendingQuestioner.put(sessionId, "questioner");
            return CompletableFuture.completedFuture(List.of(interaction("questioner", "What is your location?")));
        }

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            Object query = inputs.get("query");
            String sessionId = session.getSessionId();
            if (query instanceof InteractiveInput input) {
                Map<String, Object> answerResult = answer(sessionId, input);
                @SuppressWarnings("unchecked")
                Map<String, Object> output = (Map<String, Object>) answerResult.get("output");
                return List.of((Object) workflowFinal(output)).iterator();
            }
            String text = String.valueOf(query);
            if (text.contains("weather")) {
                appendRole(sessionId, "user");
                slowWorkflowBySession.put(sessionId, true);
                return Collections.emptyIterator();
            }
            if (text.contains("stock")) {
                if (Boolean.TRUE.equals(slowWorkflowBySession.remove(sessionId))) {
                    cancelledBySession.put(sessionId, true);
                }
                appendRole(sessionId, "user");
                appendRole(sessionId, "assistant");
                appendRole(sessionId, "assistant");
                pendingQuestioner.put(sessionId, "questioner");
                return List.of((Object) interaction("questioner", "What is the stock code?")).iterator();
            }
            appendRole(sessionId, "user");
            appendRole(sessionId, "assistant");
            return List.of((Object) workflowFinal(Map.of("response", text))).iterator();
        }

        boolean slowWorkflowStarted(String sessionId) {
            return Boolean.TRUE.equals(slowWorkflowBySession.get(sessionId));
        }

        boolean wasCancelled(String sessionId) {
            return Boolean.TRUE.equals(cancelledBySession.get(sessionId));
        }

        List<String> roles(String sessionId) {
            return List.copyOf(chatRoles.getOrDefault(sessionId, List.of()));
        }

        private Map<String, Object> answer(String sessionId, InteractiveInput input) {
            appendRole(sessionId, "user");
            appendRole(sessionId, "assistant");
            pendingQuestioner.remove(sessionId);
            Object value = input.getUserInputs().values().stream().findFirst().orElse("");
            return Map.of(
                    "result_type", "answer",
                    "output", Map.of("response", String.valueOf(value))
            );
        }

        private static OutputSchema interaction(String nodeId, String prompt) {
            return new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput(nodeId, Map.of("prompt", prompt)));
        }

        private static OutputSchema workflowFinal(Map<String, Object> payload) {
            return new OutputSchema("workflow_final", 0, payload);
        }

        private void appendRole(String sessionId, String role) {
            chatRoles.compute(sessionId, (ignored, roles) -> {
                List<String> updated = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
                updated.add(role);
                return updated;
            });
        }
    }

    private static final class RecordingSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private RecordingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            if (data != null) {
                state.putAll(data);
            }
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
