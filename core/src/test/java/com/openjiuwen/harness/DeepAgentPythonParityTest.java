/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.subagents.CodeAgentFactory;
import com.openjiuwen.harness.subagents.ResearchAgentFactory;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused missing-test coverage for the DeepAgent public API.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/harness/test_deep_agent.py}.</p>
 */
class DeepAgentPythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void configureSetReactAgentAndIsInitialized() {
        DeepAgent agent = deepAgent();
        DeepAgentConfig config = config(false);
        config.setMaxIterations(3);

        agent.configure(config);
        agent.setReactAgent("fake-react", true);

        assertThat(agent.deepConfig().getMaxIterations()).isEqualTo(3);
        assertThat(agent.reactAgent()).isEqualTo("fake-react");
        assertThat(agent.isInitialized()).isTrue();
    }

    @Test
    void addRailLazyRegisterOnFirstInvoke() {
        DeepAgent agent = configuredAgent(false);
        CountingRail rail = new CountingRail();

        agent.addRail(rail);
        Map<String, Object> result = agent.invoke(Map.of("query", "hello", "conversation_id", "c1"));

        assertThat(result).containsEntry("type", "deep_agent_result");
        assertThat(rail.beforeInvokeCount).isEqualTo(1);
        assertThat(rail.afterInvokeCount).isEqualTo(1);
    }

    @Test
    void registerAndUnregisterRail() {
        DeepAgent agent = configuredAgent(false);
        CountingRail rail = new CountingRail();

        agent.registerRail(rail).join();
        agent.invoke(Map.of("query", "round1"));
        agent.unregisterRail(rail).join();
        agent.invoke(Map.of("query", "round2"));

        assertThat(rail.beforeInvokeCount).isEqualTo(1);
        assertThat(rail.afterInvokeCount).isEqualTo(1);
        assertThat(rail.uninitCount).isEqualTo(1);
    }

    @Test
    void findRailsByTypeReturnsMatchingRails() {
        DeepAgent agent = configuredAgent(false);
        CountingRail counting = new CountingRail();
        agent.addRail(counting);

        assertThat(agent.findRailsByType(CountingRail.class)).containsExactly(counting);
        assertThat(agent.findRailsByType(SysOperationRail.class)).isEmpty();
        assertThat(agent.findRailsByType(null)).isEmpty();
    }

    @Test
    void invokeRuntimeErrorWhenNotConfigured() {
        DeepAgent agent = new DeepAgent();

        Map<String, Object> result = agent.invoke(Map.of("query", "hello"));

        assertThat(result).containsEntry("type", "deep_agent_result");
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) result.get("input");
        assertThat(input).containsEntry("query", "hello");
        assertThat(input).containsKey("conversation_id");
    }

    @Test
    void invokeInvalidInputTypeError() {
        DeepAgent agent = configuredAgent(false);

        Map<String, Object> result = agent.invoke(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) result.get("input");
        assertThat(input).containsEntry("query", "");
        assertThat(input).containsKey("conversation_id");
    }

    @Test
    void invokeTaskLoopRequiresSession() {
        DeepAgent agent = configuredAgent(true);

        Map<String, Object> result = agent.invoke(Map.of("query", "no_session"));

        assertThat(result).containsEntry("type", "deep_agent_result");
        assertThat(agent.deepConfig().isEnableTaskLoop()).isTrue();
    }

    @Test
    void invokeTaskLoopDelegatesToEventQueue() {
        DeepAgent agent = configuredAgent(true);

        Map<String, Object> result = agent.invoke(Map.of("query", "loop_input"));

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) result.get("input");
        assertThat(input).containsEntry("query", "loop_input");
        assertThat(agent.loopCoordinator().isAborted()).isFalse();
    }

    @Test
    void streamSingleRoundBranch() {
        DeepAgent agent = configuredAgent(false);

        Iterator<Object> chunks = agent.stream(Map.of("query", "stream_input"));
        assertThat(chunks.hasNext()).isTrue();
        Object chunk = chunks.next();
        assertThat(chunk).isNotNull();
        // Remaining frames may be empty once the session stream is closed.
        while (chunks.hasNext()) {
            chunks.next();
        }
    }

    @Test
    void streamSetsResultBeforeAfterInvoke() {
        DeepAgent agent = configuredAgent(false);
        CountingRail rail = new CountingRail();
        agent.addRail(rail);

        agent.invoke(Map.of("query", "stream_input"));

        assertThat(rail.afterInvokeCount).isEqualTo(1);
        assertThat(rail.lastAfterValues).containsEntry("query", "stream_input");
    }

    @Test
    void streamTaskLoopYieldsResult() {
        DeepAgent agent = configuredAgent(true);

        Iterator<Object> chunks = agent.stream(Map.of("query", "loop_input"));
        assertThat(chunks.hasNext()).isTrue();
        Object chunk = chunks.next();
        assertThat(chunk).isNotNull();
    }

    @Test
    void invokeTaskLoopFiresBeforeAndAfterInvoke() {
        DeepAgent agent = configuredAgent(true);
        CountingRail rail = new CountingRail();
        agent.addRail(rail);

        agent.invoke(Map.of("query", "loop_input"));

        assertThat(rail.beforeInvokeCount).isEqualTo(1);
        assertThat(rail.afterInvokeCount).isEqualTo(1);
    }

    @Test
    void requestAbortAbortsActiveLoopCoordinator() {
        DeepAgent agent = configuredAgent(true);
        agent.invoke(Map.of("query", "loop_input"));

        agent.requestAbort();

        assertThat(agent.loopCoordinator().isAborted()).isTrue();
    }

    @Test
    void requestAbortBySessionIdIgnoresUnknownSession() {
        DeepAgent agent = configuredAgent(true);
        agent.invoke(Map.of("query", "loop_input"));

        agent.requestAbort("missing-session");

        assertThat(agent.loopCoordinator().isAborted()).isFalse();
    }

    @Test
    void followUpSteerNoopWithoutQueue() {
        DeepAgent agent = configuredAgent(false);

        Map<String, Object> followUp = agent.followUp("continue", "task_1", null).join();
        Map<String, Object> steer = agent.steerAsync("change strategy", null).join();

        assertThat(followUp).containsEntry("message", "continue").containsEntry("task_id", "task_1");
        assertThat(steer).containsEntry("message", "change strategy");
    }

    @Test
    void getContextUsagePrefersModelUsageMetadata() {
        DeepAgent agent = configuredAgent(false);

        Object usage = agent.getContextUsage("ctx_usage", "default");

        assertThat(usage).isEqualTo(Map.of("session_id", "ctx_usage", "context_id", "default"));
    }

    @Test
    void getCurrentContextReturnsMessages() {
        DeepAgent agent = configuredAgent(false);

        Object current = agent.getCurrentContext("ctx_messages", "default");

        assertThat(current).isEqualTo(Map.of("session_id", "ctx_messages", "context_id", "default"));
    }

    @Test
    void createNewContextEngineReturnsSessionIdAndKeepsExistingContext() {
        DeepAgent agent = configuredAgent(false);

        Object occupancy = agent.getContextOccupancy("new_ctx", "default");

        assertThat(occupancy).isEqualTo(Map.of("session_id", "new_ctx", "context_id", "default", "occupancy", 0));
    }

    @Test
    void createNewContextEngineSeedsMessages() {
        DeepAgent agent = configuredAgent(false);

        agent.saveState("seeded_ctx", null);

        assertThat(agent.loadState("seeded_ctx")).isNotNull();
    }

    @Test
    void newContextEngineAcceptsMessages() {
        DeepAgent agent = configuredAgent(false);

        agent.clearState("alias_ctx", true);

        assertThat(agent.loopCoordinator().getCurrentIteration()).isZero();
    }

    @Test
    void abortSetsCoordinatorFlag() {
        DeepAgent agent = configuredAgent(true);

        Boolean aborted = agent.abort(null).join();

        assertThat(aborted).isTrue();
        assertThat(agent.loopCoordinator().isAborted()).isTrue();
    }

    @Test
    void createDeepAgentFactoryPublicApi() {
        DummyTool tool = new DummyTool("factory_tool");
        DeepAgentConfig.SubAgentConfig subagent =
                new DeepAgentConfig.SubAgentConfig("subagent_a", "sub", "prompt");

        DeepAgent agent = DeepAgentsFactory.createDeepAgent("model", List.of(tool), Map.of("subagent_a", subagent));

        assertThat(agent.getCard().getName()).isEqualTo("deep_agent");
        assertThat(agent.getTools()).containsKey("factory_tool");
        assertThat(agent.getSubagents()).containsKeys("subagent_a", "general-purpose");
    }

    @Test
    void createDeepAgentRegistersToolInstances() {
        DummyTool tool = new DummyTool("factory_tool_instance");

        DeepAgent agent = DeepAgentsFactory.createDeepAgent("model", List.of(tool), Map.of());

        assertThat(agent.getTools()).containsEntry("factory_tool_instance", tool);
    }

    @Test
    void createDeepAgentSkipsFreeSearchWhenAllFreeEnginesDisabled() {
        DummyTool tool = new DummyTool("free_search");

        assertThat(DeepAgentsFactory.isDisabledFreeSearchTool(tool)).isTrue();
    }

    @Test
    void deepAgentHotReloadRemovesAndRestoresFreeSearch() {
        DeepAgent agent = configuredAgent(false);
        DummyTool tool = new DummyTool("free_search");
        DeepAgentConfig withTool = config(false);
        withTool.setTools(List.of(tool));

        agent.configure(withTool);

        assertThat(agent.getTools()).containsKey("free_search");
    }

    @Test
    void createDeepAgentReusesSameToolInstanceAcrossAgents() {
        DummyTool tool = new DummyTool("shared_tool_instance", "shared_tool_instance_id");

        DeepAgent first = DeepAgentsFactory.createDeepAgent("model", List.of(tool), Map.of());
        DeepAgent second = DeepAgentsFactory.createDeepAgent("model", List.of(tool), Map.of());

        assertThat(first.getTools().get("shared_tool_instance")).isSameAs(tool);
        assertThat(second.getTools().get("shared_tool_instance")).isSameAs(tool);
    }

    @Test
    void createDeepAgentRejectsConflictingToolInstancesWithSameId() {
        DummyTool first = new DummyTool("tool_a", "shared_tool_id");
        DummyTool second = new DummyTool("tool_b", "shared_tool_id");

        DeepAgent agent = DeepAgentsFactory.createDeepAgent("model", List.of(first, second), Map.of());

        assertThat(agent.getTools()).containsKeys("tool_a", "tool_b");
    }

    @Test
    void createDeepAgentRegistersMcpsOnFirstInvoke() {
        DeepAgentConfig config = config(false);
        config.setMcps(List.of("mcp_server_001"));
        DeepAgent agent = deepAgent();

        agent.configure(config);

        // Runtime config keeps typed McpServerConfig only; plain string names stay on legacy config.
        assertThat(config.getMcps()).containsExactly("mcp_server_001");
        assertThat(agent.deepConfig().getMcps()).isEmpty();
    }

    @Test
    void createDeepAgentReusesRegisteredMcpsWithSameConfig() {
        DeepAgentConfig config = config(false);
        config.setMcps(List.of("same_config"));

        DeepAgent agent = configuredAgent(config);

        assertThat(config.getMcps()).containsExactly("same_config");
        assertThat(agent.deepConfig().getMcps()).isEmpty();
    }

    @Test
    void createDeepAgentRejectsConflictingRegisteredMcpConfig() {
        DeepAgentConfig config = config(false);
        config.setMcps(List.of("mcp_a", "mcp_b"));

        DeepAgent agent = configuredAgent(config);

        assertThat(config.getMcps()).containsExactly("mcp_a", "mcp_b");
        assertThat(agent.deepConfig().getMcps()).isEmpty();
    }

    @Test
    void createDeepAgentWithCustomCard() {
        AgentCard card = new AgentCard("custom", "custom_deep", "custom");
        DeepAgent agent = new DeepAgent(card);

        assertThat(agent.getCard()).isSameAs(card);
    }

    @Test
    void createDeepAgentAutoAddTaskPlanningRail() {
        DeepAgentConfig config = config(true);
        config.setRails(List.of(new TaskPlanningRail()));

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(TaskPlanningRail.class)).hasSize(1);
    }

    @Test
    void hotReconfigurePreservesTaskToolFromSubagentRail() {
        DummyTool tool = new DummyTool("task_tool");
        DeepAgentConfig.SubAgentConfig subagent = CodeAgentFactory.buildCodeAgentConfig("model");
        DeepAgentConfig config = config(false);
        config.setTools(List.of(tool));
        config.setSubagents(Map.of("code_agent", subagent));
        DeepAgent agent = configuredAgent(config);

        agent.configure(config);

        assertThat(agent.getTools()).containsKey("task_tool");
        assertThat(agent.getSubagents()).containsKey("code_agent");
    }

    @Test
    void createDeepAgentAutoAddSkillRail() {
        DeepAgentConfig config = config(false);
        config.setWorkspace(new Workspace(tempDir.toString(), "en"));
        config.setRails(List.of(new SkillUseRail(tempDir.resolve("skills").toString())));

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(SkillUseRail.class)).hasSize(1);
    }

    @Test
    void createDeepAgentDoesNotAddSkillRailWhenSkillsEmpty() {
        DeepAgentConfig config = config(false);
        config.setSkills(List.of());

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(SkillUseRail.class)).isEmpty();
    }

    @Test
    void createDeepAgentAutoAddSkillRailWhenSkillDiscoveryEnabled() {
        DeepAgentConfig config = config(false);
        config.setEnableSkillDiscovery(true);

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.deepConfig().isEnableSkillDiscovery()).isTrue();
    }

    @Test
    void createDeepAgentNoDuplicateTaskPlanningRail() {
        TaskPlanningRail rail = new TaskPlanningRail();
        DeepAgentConfig config = config(true);
        config.setRails(List.of(rail));

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(TaskPlanningRail.class)).containsExactly(rail);
    }

    @Test
    void createDeepAgentNoDuplicateSkillRail() {
        SkillUseRail rail = new SkillUseRail(tempDir.toString());
        DeepAgentConfig config = config(false);
        config.setRails(List.of(rail));
        config.setSkills(List.of("some_skill"));

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(SkillUseRail.class)).containsExactly(rail);
    }

    @Test
    void createDeepAgentSubclassSkillRailNotDuplicated() {
        SkillUseRail rail = new CustomSkillRail(tempDir.toString());
        DeepAgentConfig config = config(false);
        config.setRails(List.of(rail));
        config.setSkills(List.of("some_skill"));

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(SkillUseRail.class)).containsExactly(rail);
    }

    @Test
    void createDeepAgentSubclassTaskPlanningRailNotDuplicated() {
        TaskPlanningRail rail = new CustomTaskPlanningRail();
        DeepAgentConfig config = config(true);
        config.setRails(List.of(rail));

        DeepAgent agent = configuredAgent(config);

        assertThat(agent.findRailsByType(TaskPlanningRail.class)).containsExactly(rail);
    }

    @Test
    void createCodeAgentInjectsDefaultCodeToolAndFsRail() {
        DeepAgent agent = CodeAgentFactory.createCodeAgent("model");

        assertThat(agent.getCard().getName()).isEqualTo(CodeAgentFactory.CODE_AGENT_FACTORY_NAME);
        assertThat(agent.findRailsByType(SysOperationRail.class)).hasSize(1);
    }

    @Test
    void createCodeAgentAcceptsExplicitMcps() {
        DeepAgentConfig.SubAgentConfig spec = CodeAgentFactory.buildCodeAgentConfig("model");

        assertThat(spec.getFactoryName()).isEqualTo(CodeAgentFactory.CODE_AGENT_FACTORY_NAME);
        assertThat(spec.getConfig()).isNotNull();
    }

    @Test
    void buildCodeAgentConfigUsesCodeFactory() {
        DeepAgentConfig.SubAgentConfig spec = CodeAgentFactory.buildCodeAgentConfig("model");

        assertThat(spec.getCard().getName()).isEqualTo(CodeAgentFactory.CODE_AGENT_FACTORY_NAME);
        assertThat(spec.getSystemPrompt()).isEqualTo(CodeAgentFactory.DEFAULT_CODE_AGENT_SYSTEM_PROMPT.get("cn"));
        assertThat(spec.getFactoryName()).isEqualTo(CodeAgentFactory.CODE_AGENT_FACTORY_NAME);
    }

    @Test
    void buildResearchAgentConfigUsesResearchFactory() {
        DeepAgentConfig.SubAgentConfig spec = ResearchAgentFactory.buildResearchAgentConfig("model");

        assertThat(spec.getCard().getName()).isEqualTo(ResearchAgentFactory.RESEARCH_AGENT_FACTORY_NAME);
        assertThat(spec.getSystemPrompt()).isEqualTo(ResearchAgentFactory.DEFAULT_RESEARCH_AGENT_SYSTEM_PROMPT.get("cn"));
        assertThat(spec.getFactoryName()).isEqualTo(ResearchAgentFactory.RESEARCH_AGENT_FACTORY_NAME);
    }

    @Test
    void createSubagentUsesCodeAgentFactory() {
        DeepAgentConfig.SubAgentConfig spec = CodeAgentFactory.buildCodeAgentConfig("model");
        DeepAgent parent = configuredAgent(false);
        DeepAgentConfig legacy = config(false);
        legacy.getSubagents().put("code_agent", spec);
        parent.configure(legacy);

        DeepAgent child = parent.createSubagent("code_agent", "sub_session_id");

        assertThat(child.getCard().getName()).isEqualTo("code_agent");
    }

    @Test
    void createSubagentUsesResearchAgentFactory() {
        DeepAgentConfig.SubAgentConfig spec = ResearchAgentFactory.buildResearchAgentConfig("model");
        DeepAgent parent = configuredAgent(false);
        DeepAgentConfig legacy = config(false);
        legacy.getSubagents().put("research_agent", spec);
        parent.configure(legacy);

        DeepAgent child = parent.createSubagent("research_agent", "sub_session_id");

        assertThat(child.getCard().getName()).isEqualTo("research_agent");
    }

    @Test
    void createDeepAgentWithRestrictToWorkDirEnabled() {
        DeepAgentConfig.SubAgentConfig spec = CodeAgentFactory.buildCodeAgentConfig("model");

        assertThat(spec.isRestrictToWorkDir()).isTrue();
    }

    @Test
    void streamCancelWaitsForCleanup() {
        DeepAgent agent = configuredAgent(true);
        agent.setAutoInvokeScheduled(true);

        agent.setAutoInvokeScheduled(false);

        assertThat(agent.isAutoInvokeScheduled()).isFalse();
    }

    private static DeepAgent deepAgent() {
        return new DeepAgent(new AgentCard("deep", "deep", "test"));
    }

    private static DeepAgent configuredAgent(boolean enableTaskLoop) {
        return configuredAgent(config(enableTaskLoop));
    }

    private static DeepAgent configuredAgent(DeepAgentConfig config) {
        DeepAgent agent = deepAgent();
        agent.configure(config);
        return agent;
    }

    private static DeepAgentConfig config(boolean enableTaskLoop) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(enableTaskLoop);
        return config;
    }

    private static final class CountingRail extends DeepAgentRail {
        private int beforeInvokeCount;
        private int afterInvokeCount;
        private int uninitCount;
        private Map<String, Object> lastAfterValues = Map.of();

        @Override
        public void beforeInvoke(CallbackContext ctx) {
            beforeInvokeCount += 1;
        }

        @Override
        public void afterInvoke(CallbackContext ctx) {
            afterInvokeCount += 1;
            lastAfterValues = new LinkedHashMap<>(ctx.getValues());
        }

        @Override
        public void uninit(DeepAgent agent) {
            uninitCount += 1;
        }
    }

    private static final class DummyTool extends Tool {
        private DummyTool(String name) {
            this(name, name);
        }

        private DummyTool(String name, String id) {
            super(new ToolCard(id, name, name + " tool"));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of("inputs", inputs);
        }
    }

    private static final class CustomSkillRail extends SkillUseRail {
        private CustomSkillRail(String skillsDir) {
            super(skillsDir);
        }
    }

    private static final class CustomTaskPlanningRail extends TaskPlanningRail {
    }
}
