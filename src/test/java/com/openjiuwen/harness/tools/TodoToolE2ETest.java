package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Mirrors Python's test_todo_tool.py.
 * DeepAgent Todo tool end-to-end system test.
 */
@Tag("system-test")
class TodoToolE2ETest {

    static class ToolTraceRail extends AgentRail {
        final List<String> toolCalls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            Object inputs = ctx.getInputs();
            if (inputs instanceof Map<?, ?> map) {
                Object toolName = map.get("tool_name");
                if (toolName != null) {
                    toolCalls.add(String.valueOf(toolName));
                }
            }
        }
    }

    private static final String API_BASE = System.getenv("API_BASE") != null ? System.getenv("API_BASE") : "";
    private static final String API_KEY = System.getenv("API_KEY") != null ? System.getenv("API_KEY") : "";
    private static final String MODEL_NAME = System.getenv("MODEL_NAME") != null ? System.getenv("MODEL_NAME") : "";
    private static final String MODEL_PROVIDER = System.getenv("MODEL_PROVIDER") != null ? System.getenv("MODEL_PROVIDER") : "";

    private String sessionId;

    @BeforeEach
    void setUp() {
        Runner.start();
        sessionId = "todo_e2e_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    private static Model createModel() {
        ModelClientConfig clientConfig = new ModelClientConfig();
        clientConfig.setClientProvider(MODEL_PROVIDER);
        clientConfig.setApiKey(API_KEY);
        clientConfig.setApiBase(API_BASE);
        clientConfig.setVerifySsl(false);

        ModelRequestConfig requestConfig = new ModelRequestConfig();
        requestConfig.setModel(MODEL_NAME);
        requestConfig.setTemperature(0.2);
        requestConfig.setTopP(0.9);

        return new Model(clientConfig, requestConfig);
    }

    private void requireLlmConfig() {
        assumeTrue(!API_KEY.isEmpty() && !API_BASE.isEmpty(),
                "DeepAgent Todo E2E requires API_KEY and API_BASE in environment.");
    }

    private List<ToolCard> getTodoTools() {
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id("sys_operation_for_todo_tool")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir("./workspace").build())
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);
        Object sysOpObj = Runner.resourceMgr().getSysOperation("sys_operation_for_todo_tool", null, null);

        SysOperation sysOperation;
        if (sysOpObj instanceof SysOperation so) {
            sysOperation = so;
        } else if (sysOpObj instanceof List<?> list && !list.isEmpty()) {
            sysOperation = (SysOperation) list.get(0);
        } else {
            throw new IllegalStateException("Expected SysOperation from resource manager");
        }

        TodoCreateTool todoCreate = new TodoCreateTool(sysOperation);
        TodoListTool todoList = new TodoListTool(sysOperation);
        TodoModifyTool todoModify = new TodoModifyTool(sysOperation);

        Runner.resourceMgr().addTool(todoCreate, null);
        Runner.resourceMgr().addTool(todoList, null);
        Runner.resourceMgr().addTool(todoModify, null);

        return List.of(todoCreate.getCard(), todoList.getCard(), todoModify.getCard());
    }

    @Test
    void testTodoToolCardsCreated() {
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id("sys_operation_for_todo_tool_basic")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir("./workspace").build())
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);
        Object sysOpObj = Runner.resourceMgr().getSysOperation("sys_operation_for_todo_tool_basic", null, null);

        SysOperation sysOperation;
        if (sysOpObj instanceof SysOperation so) {
            sysOperation = so;
        } else if (sysOpObj instanceof List<?> list && !list.isEmpty()) {
            sysOperation = (SysOperation) list.get(0);
        } else {
            throw new IllegalStateException("Expected SysOperation from resource manager");
        }

        TodoCreateTool todoCreate = new TodoCreateTool(sysOperation);
        TodoListTool todoList = new TodoListTool(sysOperation);
        TodoModifyTool todoModify = new TodoModifyTool(sysOperation);

        assertNotNull(todoCreate.getCard());
        assertNotNull(todoList.getCard());
        assertNotNull(todoModify.getCard());
        assertEquals("todo_create", todoCreate.getCard().getName());
        assertEquals("todo_list", todoList.getCard().getName());
        assertEquals("todo_modify", todoModify.getCard().getName());
    }

    @Test
    @Disabled("skip system test")
    void testDeepAgentTodoCreateListModify() throws Exception {
        requireLlmConfig();

        ToolTraceRail toolTrace = new ToolTraceRail();
        Model model = createModel();
        List<ToolCard> todoToolCards = getTodoTools();

        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt(
                "\u4f60\u662f\u4e00\u4e2a\u4e25\u8c28\u7684\u4efb\u52a1\u6267\u884c\u52a9\u624b\u3002"
                        + "\u5f53\u7528\u6237\u8981\u6c42\u4f7f\u7528todo\u5de5\u5177\u65f6\uff0c\u5fc5\u987b\u8c03\u7528\u5de5\u5177\uff0c\u4e0d\u8981\u51ed\u7a7a\u5047\u8bbe\u3002"
        );
        config.setTools(todoToolCards);
        config.setRails(List.of(toolTrace));
        config.setMaxIterations(10);

        AgentCard card = new AgentCard();
        config.setCard(card);

        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        Session session = new AgentSessionApi(sessionId, null, null);

        String query = "\u8bf7\u4e25\u683c\u6309\u987a\u5e8f\u6267\u884c\u4ee5\u4e0b\u4efb\u52a1\uff0c\u5e76\u4e14\u6bcf\u4e00\u6b65\u90fd\u5fc5\u987b\u8c03\u7528\u5de5\u5177\uff1a\n"
                + "1. \u521b\u5efa\u4e00\u4e2a\u5f85\u529e\u4e8b\u9879\u5217\u8868\uff0c\u5305\u542b3\u4e2a\u4efb\u52a1\uff1a\u5b8c\u6210\u9700\u6c42\u5206\u6790\u3001\u7f16\u5199\u4ee3\u7801\u3001\u6d4b\u8bd5\u9a8c\u8bc1\uff1b\n"
                + "2. \u5217\u51fa\u5f53\u524d\u7684\u5f85\u529e\u4e8b\u9879\uff1b\n"
                + "3. \u4fee\u6539\u7b2c\u4e00\u4e2a\u4efb\u52a1\u7684\u72b6\u6001\u4e3a\u5df2\u5b8c\u6210\uff1b\n"
                + "4. \u518d\u6b21\u5217\u51fa\u5f85\u529e\u4e8b\u9879\u786e\u8ba4\u4fee\u6539\uff1b\n"
                + "5. \u6700\u540e\u8f93\u51fa\u4e00\u53e5\u4e2d\u6587\u603b\u7ed3\u3002";

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", query);
        Object result = Runner.runAgent(agent, inputs, session, null);

        assertInstanceOf(Map.class, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("answer", resultMap.get("result_type"));
        assertTrue(resultMap.containsKey("output"));
        assertNotNull(resultMap.get("output"));
        assertTrue(!String.valueOf(resultMap.get("output")).isEmpty());

        Map<String, Integer> toolCounts = new HashMap<>();
        for (String call : toolTrace.toolCalls) {
            toolCounts.merge(call, 1, Integer::sum);
        }
        assertTrue(toolCounts.getOrDefault("todo_create", 0) >= 1);
        assertTrue(toolCounts.getOrDefault("todo_list", 0) >= 2);
        assertTrue(toolCounts.getOrDefault("todo_modify", 0) >= 1);
        int totalCalls = toolCounts.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(totalCalls >= 4);
    }
}
