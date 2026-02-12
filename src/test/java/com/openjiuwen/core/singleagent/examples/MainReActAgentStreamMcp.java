// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.examples;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;

/**
 * ReActAgent stream 模式调用 MCP Server 示例。
 *
 * <p>整体流程：
 * <ol>
 *   <li>构造 ModelClientConfig / ModelRequestConfig / ReActAgentConfig</li>
 *   <li>创建 ReActAgent</li>
 *   <li>注册 MCP Server 并添加为 ability</li>
 *   <li>执行流式调用</li>
 * </ol>
 *
 * <p>前置条件：需要先启动 MCP SSE Server（对应 mcp_server.py），监听 http://127.0.0.1:8188/sse
 *
 * <p>对应 Python: agent-core/examples/test_examples_for_java/react_agent/test/main_react_agent_stream_mcp.py
 */
public class MainReActAgentStreamMcp {

    private static final String API_BASE = System.getenv().getOrDefault(
        "API_BASE", "https://api.siliconflow.cn/v1/chat/completions");
    private static final String API_KEY = System.getenv().getOrDefault(
        "API_KEY", "sk-");
    private static final String MODEL_NAME = System.getenv().getOrDefault(
        "MODEL_NAME", "Qwen/Qwen3-8B");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault(
        "MODEL_PROVIDER", "SiliconFlow");

    /**
     * 工具类：创建提示词模板
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

        // 5. 创建并注册 MCP Server
        McpServerConfig mcpConfig = McpServerConfig.builder()
            .serverId("yzq_mcp_server")
            .serverName("McpSseServer")
            .serverPath("http://127.0.0.1:8188")
            .build();

        Runner.getResourceMgr().addMcpServer(mcpConfig, null, 6000000.0).get();

        try {
            // 6. 添加 MCP Server 到 Agent ability
//            reactAgent.addAbility(mcpConfig);
            reactAgent.getAbilityManager().add(mcpConfig);

            // 7. 流式调用（对齐 Python: async for chunk in Runner.run_agent_streaming(agent=react_agent, inputs=...)）
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("query", "北京天气怎么样");
            inputs.put("conversation_id", "013");

            Iterator<Object> chunkIterator = Runner.runAgentStreaming(reactAgent, inputs);
            while (chunkIterator.hasNext()) {
                Object chunk = chunkIterator.next();
                System.out.println("ReActAgent chunk: " + chunk);
            }
        } finally {
            // 清理：移除 MCP Server（skipIfNotExists=true 防止 server 未注册时抛异常）
            Runner.getResourceMgr().removeMcpServer(
                "yzq_mcp_server", null, null, null, true, true).get();
        }
    }
}

