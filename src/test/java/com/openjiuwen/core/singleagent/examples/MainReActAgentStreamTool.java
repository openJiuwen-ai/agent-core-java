// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.examples;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;
import java.util.function.Function;

/**
 * ReActAgent stream 模式调用本地 Tool 示例。
 *
 * <p>整体流程：
 * <ol>
 *   <li>定义加法工具（LocalFunction）</li>
 *   <li>构造 ModelClientConfig / ModelRequestConfig / ReActAgentConfig</li>
 *   <li>创建 ReActAgent，注册工具并执行 stream</li>
 * </ol>
 *
 * <p>对应 Python: agent-core/examples/test_examples_for_java/react_agent/test/main_react_agent_stream_tool.py
 */
public class MainReActAgentStreamTool {

    private static final String API_BASE = System.getenv().getOrDefault(
        "API_BASE", "https://api.siliconflow.cn/v1/chat/completions");
    private static final String API_KEY = System.getenv().getOrDefault(
        "API_KEY", "sk-");
    private static final String MODEL_NAME = System.getenv().getOrDefault(
        "MODEL_NAME", "deepseek-ai/DeepSeek-V3");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault(
        "MODEL_PROVIDER", "SiliconFlow");

    /**
     * 工具类：创建提示词模板和工具
     */
    static class Utils {

        /**
         * 创建提示词模板
         */
        static List<Map<String, Object>> createPromptTemplate() {
            String systemPrompt = "你是一个AI助手，在适当的时候调用合适的工作流，帮助我完成任务！"
                + "注意：只需要调用一次工作流后就进行总结，不要重复调用！";
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", "system");
            msg.put("content", systemPrompt);
            return List.of(msg);
        }

        /**
         * 创建加法工具
         */
        static LocalFunction createTool() {
            Map<String, Object> propA = new LinkedHashMap<>();
            propA.put("type", "number");
            propA.put("description", "加数");

            Map<String, Object> propB = new LinkedHashMap<>();
            propB.put("type", "number");
            propB.put("description", "被加数");

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("a", propA);
            properties.put("b", propB);

            Map<String, Object> inputParams = new LinkedHashMap<>();
            inputParams.put("type", "object");
            inputParams.put("properties", properties);
            inputParams.put("required", List.of("a", "b"));

            ToolCard addCard = new ToolCard("add_id", "add_2025", "加法", inputParams);

            Function<Map<String, Object>, Object> addFunc = args -> {
                double a = ((Number) args.get("a")).doubleValue();
                double b = ((Number) args.get("b")).doubleValue();
                return a + b;
            };

            return new LocalFunction(addCard, addFunc);
        }
    }

    /**
     * 主函数
     */
    public static void main(String[] args) throws Exception {
        // 1. 创建 AgentCard
        AgentCard agentCard = new AgentCard("react_agent_1234", "react_agent_1234", "AI计算助手", null);

        // 2. 创建模型配置
        ModelRequestConfig modelConfig = new ModelRequestConfig.Builder()
            .model(MODEL_NAME)
            .temperature(0.6)
            .topP(0.8)
            .build();

        ModelClientConfig modelClient = new ModelClientConfig.Builder()
            .clientProvider(MODEL_PROVIDER)
            .apiBase(API_BASE)
            .apiKey(API_KEY)
            .verifySsl(false)
            .build();

        // 3. 创建 ReActAgentConfig（对齐 Python: ReActAgentConfig(model_client_config=..., ...)）
        List<Map<String, Object>> promptTemplate = Utils.createPromptTemplate();
        ReActAgentConfig reactAgentConfig = ReActAgentConfig.builder()
            .modelClientConfig(modelClient)
            .modelConfigObj(modelConfig)
            .promptTemplate(promptTemplate)
            .modelName(MODEL_NAME)
            .build();

        // 4. 创建 ReActAgent 并配置
        ReActAgent reactAgent = new ReActAgent(agentCard);
        reactAgent.configure(reactAgentConfig);

        // 5. 创建并注册工具
        LocalFunction tool = Utils.createTool();
        Runner.getResourceMgr().addTool(tool, null, null);
//        reactAgent.addAbility(tool.getCard());
        reactAgent.getAbilityManager().add(tool.getCard());

        // 6. 流式调用（对齐 Python: async for chunk in Runner.run_agent_streaming(agent=react_agent, inputs=...)）
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", "请帮我计算1056 + 7890的结果");
        inputs.put("conversation_id", "013");

        Iterator<Object> chunkIterator = Runner.runAgentStreaming(reactAgent, inputs);
        while (chunkIterator.hasNext()) {
            Object chunk = chunkIterator.next();
            System.out.println("ReActAgent chunk: " + chunk);
        }
    }
}

