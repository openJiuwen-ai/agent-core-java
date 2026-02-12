// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🌤️ Mood Assistant with openJiuwen (ReAct Agent)
 * 
 * <p>本示例展示如何基于 openJiuwen 框架构建一个简单的对话助手（心情树洞）。
 * 
 * <p>整体流程：
 * <ol>
 *   <li>定义模型、提示词与 Agent 配置</li>
 *   <li>创建 ReActAgent</li>
 *   <li>执行单次查询演示</li>
 * </ol>
 * 
 * <p>对应 Python: agent-core/examples/test_examples_for_java/react_agent/react_agent.py
 */
public class ReActAgentExample {
    
    // ===== 按需修改 =====
    private static final String API_BASE = System.getenv().getOrDefault(
        "API_BASE", "https://api.siliconflow.cn/v1");
    private static final String API_KEY = System.getenv().getOrDefault(
        "API_KEY", "sk-apikey");
    private static final String MODEL_NAME = System.getenv().getOrDefault(
        "MODEL_NAME", "deepseek-ai/DeepSeek-V3");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault(
        "MODEL_PROVIDER", "SiliconFlow");
    
    /**
     * 定义模型配置
     * 
     * @return 模型配置 Map
     */
    public static Map<String, String> buildModelConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("model_name", MODEL_NAME);
        config.put("model_provider", MODEL_PROVIDER);
        config.put("api_base", API_BASE);
        config.put("api_key", API_KEY);
        return config;
    }
    
    /**
     * 定义提示词
     * 
     * @return 提示词消息列表
     */
    public static List<Map<String, Object>> buildPrompt() {
        String template = "你是一个心情树洞，能够回复用户的问话";
        
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", template);
        
        return List.of(systemMessage);
    }
    
    /**
     * 创建心情树洞 Agent
     * 
     * @return ReActAgent 实例和配置的数组 [agent, config]
     */
    public static Object[] createMoodAgent() {
        Map<String, String> modelCfg = buildModelConfig();
        
        // 1. 创建 Agent Card
        AgentCard agentCard = new AgentCard("mood_agent", "Jiuwen 心情树洞");
        
        // 2. 创建 Agent 配置
        ReActAgentConfig agentConfig = new ReActAgentConfig();
        agentConfig.configureModelClient(
            modelCfg.get("model_provider"),
            modelCfg.get("api_key"),
            modelCfg.get("api_base"),
            modelCfg.get("model_name"),
            false  // verifySsl
        );
        agentConfig.configurePromptTemplate(buildPrompt());
        agentConfig.configureMaxIterations(5);
        
        // 3. 创建 Agent 并应用配置
        ReActAgent agent = new ReActAgent(agentCard);
        agent.configure(agentConfig);
        
        return new Object[]{agent, agentConfig};
    }
    
    /**
     * 执行单次查询
     * 
     * @param agent ReActAgent 实例
     * @param query 用户查询
     * @return 查询结果
     */
    public static Object runSingleQuery(ReActAgent agent, String query) throws Exception {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("🔍 查询: " + query);
        System.out.println("=".repeat(50));
        
        Map<String, Object> inputs = Map.of("query", query);
        Object result = agent.invoke(inputs, null).get();
        
        System.out.println("📋 结果: " + result);
        return result;
    }
    
    /**
     * 执行多次查询
     * 
     * @param agent ReActAgent 实例
     */
    public static void runMultipleQueries(ReActAgent agent) throws Exception {
        String[] queries = {"你好啊", "你好啊", "你好啊"};
        
        for (String query : queries) {
            runSingleQuery(agent, query);
        }
    }
    
    /**
     * 主函数
     */
    public static void main(String[] args) throws Exception {
        System.out.println("🌤️ Mood Assistant with openJiuwen (ReAct Agent)");
        System.out.println("=".repeat(60));
        
        // 1. 创建 Agent
        System.out.println("\n🤖 创建 ReAct Agent...");
        Object[] agentAndConfig = createMoodAgent();
        ReActAgent moodAgent = (ReActAgent) agentAndConfig[0];
        
        // 2. 执行查询
        System.out.println("\n🚀 开始执行查询...");
        runSingleQuery(moodAgent, "你好啊");
        
        // 3. 可选：保存配置
        // saveAgentConfig(agentConfig);
        
        System.out.println("\n✅ 完成!");
    }
}
