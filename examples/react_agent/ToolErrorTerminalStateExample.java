/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.react_agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.List;
import java.util.Map;

public final class ToolErrorTerminalStateExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "tool_error_example_agent";
    private static final String TOOL_ID = "failing_calculator";
    private static final String CONVERSATION_ID = "tool_error_example_001";
    private static final String SYSTEM_PROMPT = "你是一个计算助手。当用户要求计算时，必须先调用 Calculator 工具获取结果，"
            + "再基于工具结果给出最终回答。每次只调用一次工具。";

    private ToolErrorTerminalStateExample() {
    }

    public static void main(String[] args) throws Exception {
        boolean failTaskOnToolError = args.length > 0 && "fail".equalsIgnoreCase(args[0]);

        System.out.println("===== 工具异常终态示例 =====");
        System.out.println("配置: shouldFailTaskOnToolError=" + failTaskOnToolError);
        System.out.println();

        Tool failingTool = null;
        try {
            ReActAgent agent = createAgent(failTaskOnToolError);
            failingTool = createFailingTool();
            registerTool(agent, failingTool);

            String query = "请帮我计算 1+1 等于多少";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                    agent,
                    Map.of(
                            "query", query,
                            "conversation_id", CONVERSATION_ID
                    ),
                    null,
                    null
            );

            System.out.println("Agent 返回结果:");
            System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));

            Object resultType = result.get("result_type");
            String terminalState = "error".equals(resultType) ? "FAILED" : "COMPLETED";
            System.out.println();
            System.out.println(">>> result_type = " + resultType);
            System.out.println(">>> 任务终态    = " + terminalState);
        } finally {
            if (failingTool != null) {
                Runner.resourceMgr().removeTool(failingTool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
            }
            Runner.release(CONVERSATION_ID);
            Runner.stop();
        }
    }

    private static ReActAgent createAgent(boolean failTaskOnToolError) {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("计算助手-工具异常示例")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .maxIterations(3)
                .shouldFailTaskOnToolError(failTaskOnToolError)
                .build()
                .configureModelClient(
                        ExampleApiConfigLoader.getModelProvider(),
                        ExampleApiConfigLoader.getApiKey(),
                        ExampleApiConfigLoader.getApiBase(),
                        ExampleApiConfigLoader.getModelName(),
                        ExampleApiConfigLoader.getSslVerify()
                );

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        requestConfig.setTemperature(0.6);
        requestConfig.setTopP(0.8);
        requestConfig.setMaxTokens(1024);

        agent.configure(config);
        return agent;
    }

    private static Tool createFailingTool() {
        ToolCard card = ToolCard.builder()
                .id(TOOL_ID)
                .name("Calculator")
                .description("计算器工具，输入 expression 进行数学计算，例如 1+1")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "expression", Map.of(
                                        "type", "string",
                                        "description", "要计算的数学表达式，例如 1+1"
                                )
                        ),
                        "required", List.of("expression")
                ))
                .build();

        return new LocalFunction(card, inputs -> {
            throw new IllegalStateException("模拟工具执行异常: 数据库连接失败，无法完成计算");
        });
    }

    private static void registerTool(ReActAgent agent, Tool tool) {
        Runner.resourceMgr().removeTool(tool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addTool(tool, AGENT_ID);
        agent.getAbilityManager().add(tool.getCard());
    }
}
