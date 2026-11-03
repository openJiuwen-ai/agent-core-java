/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.agent;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the legacy agent compatibility layer.
 *
 * <p>Mirrors Python's {@code WorkflowFactory}, {@code BaseAgent}, and
 * {@code ControllerAgent} in
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
class LegacyAgentTest {

    @Test
    void workflowFactoryReturnsNewWorkflowAndPreservesCardMetadata() {
        WorkflowFactory factory = WorkflowFactory.workflowProvider(
                "wf", "1.0", "Workflow", "demo", Map.of("type", "object"), Workflow::new);

        Object first = factory.get();
        Object second = factory.get();

        assertTrue(first instanceof Workflow);
        assertTrue(second instanceof Workflow);
        assertNotSame(first, second);
        assertEquals("wf", factory.card().getId());
        assertEquals("Workflow", factory.card().getName());
        assertEquals("demo", factory.card().getDescription());
        assertEquals("1.0", factory.card().getVersion());
        assertEquals(Map.of("type", "object"), factory.card().getInputParams());
    }

    @Test
    void baseAgentWrapsConfigAndTracksToolsPluginsAndWorkflows() {
        FakeConfig config = new FakeConfig();
        TestBaseAgent agent = new TestBaseAgent(config);
        FakeTool tool = new FakeTool("tool-id", "tool-name", "desc");
        WorkflowFactory workflow = new WorkflowFactory("wf", "1", Workflow::new);

        agent.addPrompt(List.of(Map.of("role", "system", "content", "hello")));
        agent.addTools(List.of(tool));
        agent.addTools(List.of(tool));
        agent.addWorkflows(List.of(workflow));

        assertSame(config, agent.config().getAgentConfig());
        assertEquals(List.of(Map.of("role", "system", "content", "hello")), config.promptTemplate);
        assertEquals(List.of("tool-name"), config.tools);
        assertEquals(1, config.plugins.size());
        assertEquals("tool-name", config.plugins.get(0).get("name"));
        assertEquals(1, agent.getTools().size());
        assertEquals(1, agent.getWorkflows().size());
        assertEquals("wf", config.workflows.get(0).getId());
    }

    @Test
    void removeWorkflowsUsesWorkflowIdAndVersionLikePythonTupleKeys() {
        FakeConfig config = new FakeConfig();
        TestBaseAgent agent = new TestBaseAgent(config);
        WorkflowFactory keep = new WorkflowFactory("keep", "1", Workflow::new);
        WorkflowFactory remove = new WorkflowFactory("remove", "2", Workflow::new);
        agent.addWorkflows(List.of(keep, remove));

        agent.removeWorkflows(List.of(new WorkflowReference("remove", "2")));

        assertEquals(List.of(keep), agent.getWorkflows());
        assertEquals(List.of(keep.card()), config.workflows);
    }

    @Test
    void addWorkflowsRejectsEmptyAgentIdLikePythonResourceManager() {
        FakeConfig config = new FakeConfig();
        config.id = "";
        TestBaseAgent agent = new TestBaseAgent(config);
        WorkflowFactory workflow = new WorkflowFactory("empty-tag-workflow", "1", Workflow::new);

        BaseError error = assertThrows(BaseError.class, () -> agent.addWorkflows(List.of(workflow)));

        assertEquals(StatusCode.RESOURCE_TAG_VALUE_INVALID.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("tag is invalid, tag="));
        assertTrue(error.getMessage().contains("is None or empty value"));
    }

    @Test
    void controllerAgentSetsUpControllerAndDelegatesInvokeAndStream() {
        FakeConfig config = new FakeConfig();
        RecordingController controller = new RecordingController();
        ControllerAgent agent = new ControllerAgent(config, controller);
        FakeSession session = new FakeSession("s1");

        Object invokeResult = agent.invoke(Map.of("query", "hello"), session).toCompletableFuture().join();
        Iterator<Object> iterator = agent.stream(Map.of("query", "hello"), session, List.of());

        assertSame(agent, controller.agent);
        assertSame(session, controller.session);
        assertEquals("invoked:hello", invokeResult);
        assertEquals("chunk:hello", iterator.next());
    }

    static final class FakeConfig {
        private final List<Map<String, Object>> promptTemplate = new ArrayList<>();
        private final List<String> tools = new ArrayList<>();
        private final List<Map<String, Object>> plugins = new ArrayList<>();
        private final List<com.openjiuwen.core.workflow.WorkflowCard> workflows = new ArrayList<>();
        private final FakeConstrain constrain = new FakeConstrain();
        private String id = "agent-id";
        private String description = "agent description";

        public List<Map<String, Object>> getPromptTemplate() {
            return promptTemplate;
        }

        public List<String> getTools() {
            return tools;
        }

        public List<Map<String, Object>> getPlugins() {
            return plugins;
        }

        public List<com.openjiuwen.core.workflow.WorkflowCard> getWorkflows() {
            return workflows;
        }

        public FakeConstrain getConstrain() {
            return constrain;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }

    static final class FakeConstrain {
        public int getReservedMaxChatRounds() {
            return 7;
        }
    }

    static final class FakeTool {
        private final FakeToolCard card;
        private final List<Map<String, Object>> params = List.of(
                Map.of("name", "input", "type", "string", "description", "query", "required", true));

        FakeTool(String id, String name, String description) {
            this.card = new FakeToolCard(id, name, description);
        }

        public FakeToolCard getCard() {
            return card;
        }

        public List<Map<String, Object>> getParams() {
            return params;
        }
    }

    static final class FakeToolCard {
        private final String id;
        private final String name;
        private final String description;

        FakeToolCard(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    static final class TestBaseAgent extends BaseAgent {
        TestBaseAgent(Object agentConfig) {
            super(agentConfig);
        }

        @Override
        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            return java.util.concurrent.CompletableFuture.completedFuture(inputs);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                       List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            return List.<Object>of(inputs).iterator();
        }
    }

    static final class RecordingController {
        private Object agent;
        private AgentSessionApi session;

        public void setupFromAgent(Object agent) {
            this.agent = agent;
        }

        public Object invoke(Map<String, Object> inputs, AgentSessionApi session) {
            this.session = session;
            return "invoked:" + inputs.get("query");
        }

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                       List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            this.session = session;
            return List.<Object>of("chunk:" + inputs.get("query")).iterator();
        }
    }

    static final class FakeSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        FakeSession(String sessionId) {
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
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
