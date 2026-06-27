/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.interrupt.AskUserRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.RejectResult;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.subagents.CodeAgentFactory;
import com.openjiuwen.harness.tools.FilesystemTools;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code TestCodeAgentExecutionModeMock} in
 * {@code tests/unit_tests/harness/test_code_agent_execution_mode.py}.</p>
 */
class CodeAgentExecutionModePythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @Test
    void testManualSwitchModeShowsSwitchingScene() throws Exception {
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = createAgent(trace);
        MemorySession session = createSession();

        assertThat(agent.loadState(session).getPlanMode().getMode()).isEqualTo("normal");

        agent.switchMode(session, AgentMode.PLAN);
        assertThat(agent.loadState(session).getPlanMode().getMode()).isEqualTo("plan");
        preparePlanFile(agent, session, "# 初始计划\n- step 1");

        Map<String, Object> result = agent.invoke(Map.of("query", "当前是什么模式？", "session", session)).join();

        assertThat(result).containsEntry("type", "deep_agent_result")
                .containsEntry("mode", "plan");
        assertThat(trace.toolCalls).doesNotContain("switch_mode");
    }

    @Test
    void testUserCanInvokeAgainToUpdatePlan() throws Exception {
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = createAgent(trace);
        MemorySession session = createSession();
        agent.switchMode(session, AgentMode.PLAN);
        Path planPath = preparePlanFile(agent, session, "# 计划\n- 先确认城市");

        AskUserRail askUserRail = askUserRail(agent);
        ToolCall askFeature = askUserToolCall(
                "ask_feature_1",
                List.of(question(
                        "天气",
                        "是否需要天气模块？",
                        List.of(
                                option("需要", "添加天气模块"),
                                option("不需要", "不添加天气模块")
                        )
                ))
        );
        trace.record("ask_user", Map.of("questions", List.of("是否需要天气模块？")));
        InterruptResult interrupt = (InterruptResult) askUserRail.resolveInterrupt(null, askFeature, null);

        RejectResult resumedAnswer = resumeAnswer(askUserRail, askFeature, interrupt, "需要", "是否需要天气模块？");
        assertThat(resumedAnswer.toolResult()).asString().contains("需要");

        trace.record("read_file", Map.of("path", relativePlanPath(planPath)));
        ToolOutput read = output(new FilesystemTools.ReadFileTool(tempDir.toString())
                .invoke(Map.of("path", relativePlanPath(planPath)), Map.of("session", session)));
        assertThat(read.isSuccess()).isTrue();
        assertThat(read.getData()).asString().contains("先确认城市");

        trace.record("write_file", Map.of("path", relativePlanPath(planPath)));
        ToolOutput write = output(new FilesystemTools.WriteFileTool(tempDir.toString())
                .invoke(Map.of(
                        "path", relativePlanPath(planPath),
                        "content", "# 更新后计划\n- 展示北京\n- 增加天气模块"
                ), Map.of("session", session)));

        Map<String, Object> secondInvoke = agent.invoke(Map.of("query", "继续更新计划并写入", "session", session))
                .join();

        assertThat(write.isSuccess()).isTrue();
        assertThat(secondInvoke).containsEntry("mode", "plan");
        assertThat(trace.toolCalls).contains("ask_user", "write_file");
        assertThat(Files.readString(planPath, StandardCharsets.UTF_8)).contains("天气模块");
    }

    @Test
    void testUserInteractsViaAskUserInMultiSession() throws Exception {
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = createAgent(trace);
        MemorySession session1 = createSession();
        MemorySession session2 = new MemorySession(session1.getSessionId(), session1.state);
        agent.switchMode(session1, AgentMode.PLAN);
        preparePlanFile(agent, session1, "# 初始计划\n- 城市待确认");

        AskUserRail askUserRail = askUserRail(agent);
        ToolCall askCity = askUserToolCall("ask_city_1", List.of(question(
                "城市",
                "你希望展示哪个城市？",
                List.of(option("上海", "展示上海城市信息"))
        )));
        trace.record("ask_user", Map.of("question", "你希望展示哪个城市？"));
        InterruptResult interrupt = (InterruptResult) askUserRail.resolveInterrupt(null, askCity, null);
        RejectResult answer = resumeAnswer(askUserRail, askCity, interrupt, "上海", "你希望展示哪个城市？");

        Map<String, Object> result = agent.invoke(Map.of("query", "收到你的反馈", "session", session2)).join();

        assertThat(answer.toolResult()).asString().contains("上海");
        assertThat(result).containsEntry("mode", "plan");
        assertThat(trace.toolCalls).contains("ask_user");
        assertThat(agent.loadState(session2).getPlanMode().getMode()).isEqualTo("plan");
    }

    @Test
    void testUserInteractsViaAskUserToUpdatePlan() throws Exception {
        ToolTraceRail trace = new ToolTraceRail();
        DeepAgent agent = createAgent(trace);
        MemorySession session = createSession();
        agent.switchMode(session, AgentMode.PLAN);
        preparePlanFile(agent, session, "# 初始计划\n- 城市待确认");

        AskUserRail askUserRail = askUserRail(agent);
        ToolCall askCity = askUserToolCall("ask_city_1", List.of(question(
                "城市",
                "你希望展示哪个城市？",
                List.of(
                        option("北京", "展示北京城市信息"),
                        option("上海", "展示上海城市信息")
                )
        )));
        trace.record("ask_user", Map.of("question", "你希望展示哪个城市？"));
        InterruptResult interrupt = (InterruptResult) askUserRail.resolveInterrupt(null, askCity, null);
        RejectResult answer = resumeAnswer(askUserRail, askCity, interrupt, "上海", "你希望展示哪个城市？");

        Map<String, Object> result = agent.invoke(Map.of("query", "继续完善计划", "session", session)).join();

        assertThat(answer.toolResult()).asString().contains("\"你希望展示哪个城市？\"=\"上海\"");
        assertThat(result).containsEntry("mode", "plan");
        assertThat(trace.toolCalls).contains("ask_user");
        assertThat(agent.loadState(session).getPlanMode().getMode()).isEqualTo("plan");
    }

    private DeepAgent createAgent(ToolTraceRail trace) {
        return CodeAgentFactory.createCodeAgent(
                "mock-model",
                null,
                null,
                null,
                null,
                null,
                List.of(trace),
                true,
                12,
                new Workspace(tempDir.toString(), "cn"),
                null,
                null,
                null,
                "cn",
                null,
                null
        );
    }

    private MemorySession createSession() {
        return new MemorySession("code_agent_mode_" + UUID.randomUUID().toString().replace("-", ""));
    }

    private Path preparePlanFile(DeepAgent agent, MemorySession session, String content) throws Exception {
        DeepAgentState state = agent.loadState(session);
        if (state.getPlanMode().getPlanSlug() == null || state.getPlanMode().getPlanSlug().isBlank()) {
            state.getPlanMode().setPlanSlug("mock-plan");
            agent.saveState(session, state);
        }
        Path planPath = Path.of(agent.getPlanFilePath(session));
        Files.createDirectories(planPath.getParent());
        Files.writeString(planPath, content, StandardCharsets.UTF_8);
        return planPath;
    }

    private String relativePlanPath(Path planPath) {
        return tempDir.relativize(planPath).toString();
    }

    private static AskUserRail askUserRail(DeepAgent agent) {
        return (AskUserRail) agent.findRailsByType(AskUserRail.class).getFirst();
    }

    private static RejectResult resumeAnswer(
            AskUserRail rail,
            ToolCall toolCall,
            InterruptResult interrupt,
            String answer,
            String questionText
    ) {
        AskUserRail.AskUserRequest request = (AskUserRail.AskUserRequest) interrupt.request();
        assertThat(request.getQuestions()).hasSize(1);
        InterruptDecision decision = rail.resolveInterrupt(
                null,
                toolCall,
                Map.of("answers", Map.of(questionText, answer))
        );
        return (RejectResult) decision;
    }

    private static ToolCall askUserToolCall(String id, List<Map<String, Object>> questions) throws Exception {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name("ask_user")
                .arguments(OBJECT_MAPPER.writeValueAsString(Map.of("questions", questions)))
                .index(0)
                .build();
    }

    private static Map<String, Object> question(
            String header,
            String question,
            List<Map<String, Object>> options
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("header", header);
        value.put("question", question);
        value.put("options", options);
        return value;
    }

    private static Map<String, Object> option(String label, String description) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("label", label);
        value.put("description", description);
        return value;
    }

    private static ToolOutput output(Object value) {
        return (ToolOutput) value;
    }

    private static final class ToolTraceRail extends DeepAgentRail {
        private final List<String> toolCalls = new java.util.ArrayList<>();

        @Override
        public void beforeToolCall(CallbackContext ctx) {
            if (ctx != null && ctx.get("tool_name") != null) {
                toolCalls.add(String.valueOf(ctx.get("tool_name")));
            }
        }

        private void record(String toolName, Map<String, Object> toolArgs) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("tool_name", toolName);
            values.put("tool_args", toolArgs);
            beforeToolCall(new CallbackContext(null, values));
        }
    }

    private static final class MemorySession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state;

        private MemorySession(String sessionId) {
            this(sessionId, new LinkedHashMap<>());
        }

        private MemorySession(String sessionId, Map<String, Object> state) {
            this.sessionId = sessionId;
            this.state = state;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            if (key == null) {
                return new LinkedHashMap<>(state);
            }
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
            return Collections.emptyIterator();
        }
    }
}
