// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.examples;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;
import java.util.function.Function;

/**
 * 🧮 Calculator Assistant with openJiuwen (ReAct Agent)
 *
 * <p>本示例展示如何基于 openJiuwen 框架构建一个使用本地计算器工具的数学助手。
 *
 * <p>整体流程：
 * <ol>
 *   <li>定义本地计算器工具（加法、减法、乘法、除法）</li>
 *   <li>定义模型、提示词与 Agent 配置</li>
 *   <li>创建 ReActAgent 并注册工具</li>
 *   <li>执行数学题查询演示</li>
 * </ol>
 *
 */
public class ReActAgentExample {

    // ===== 按需修改 =====
    private static final String API_BASE = System.getenv().getOrDefault(
            "API_BASE", "https://api.siliconflow.cn/v1");
    private static final String API_KEY = System.getenv().getOrDefault(
            "API_KEY", "sk-");
    private static final String MODEL_NAME = System.getenv().getOrDefault(
            "MODEL_NAME", "deepseek-ai/DeepSeek-V3");
    private static final String MODEL_PROVIDER = System.getenv().getOrDefault(
            "MODEL_PROVIDER", "SiliconFlow");

    /**
     * 辅助函数：将参数转换为数字
     *
     * @param value 输入值（Number 或 String）
     * @return 浮点数值
     * @throws IllegalArgumentException 如果无法转换
     */
    private static double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法将 '" + value + "' 转换为数字");
        }
    }

    /**
     * 创建计算器工具集合
     *
     * @return 计算器工具列表（add, subtract, multiply, divide）
     */
    public static List<LocalFunction> createCalculatorTools() {
        // 输入参数 schema 构建辅助方法
        // 加法工具
        Function<Map<String, Object>, Object> addFunc = args ->
                toNumber(args.get("a")) + toNumber(args.get("b"));

        LocalFunction addTool = new LocalFunction(
                new ToolCard("add", "计算两个数字的和（加法运算）",
                        buildTwoNumberParamsSchema("第一个数字", "第二个数字")),
                addFunc
        );

        // 减法工具
        Function<Map<String, Object>, Object> subtractFunc = args ->
                toNumber(args.get("a")) - toNumber(args.get("b"));

        LocalFunction subtractTool = new LocalFunction(
                new ToolCard("subtract", "计算两个数字的差（减法运算）",
                        buildTwoNumberParamsSchema("被减数", "减数")),
                subtractFunc
        );

        // 乘法工具
        Function<Map<String, Object>, Object> multiplyFunc = args ->
                toNumber(args.get("a")) * toNumber(args.get("b"));

        LocalFunction multiplyTool = new LocalFunction(
                new ToolCard("multiply", "计算两个数字的积（乘法运算）",
                        buildTwoNumberParamsSchema("第一个数字", "第二个数字")),
                multiplyFunc
        );

        // 除法工具
        Function<Map<String, Object>, Object> divideFunc = args -> {
            double b = toNumber(args.get("b"));
            if (b == 0) {
                return "错误：除数不能为0";
            }
            return toNumber(args.get("a")) / b;
        };

        LocalFunction divideTool = new LocalFunction(
                new ToolCard("divide", "计算两个数字的商（除法运算）",
                        buildTwoNumberParamsSchema("被除数", "除数（不能为0）")),
                divideFunc
        );

        return List.of(addTool, subtractTool, multiplyTool, divideTool);
    }

    /**
     * 构建双参数 number 类型的 JSON Schema
     *
     * @param descA 参数 a 的描述
     * @param descB 参数 b 的描述
     * @return JSON Schema Map
     */
    private static Map<String, Object> buildTwoNumberParamsSchema(String descA, String descB) {
        Map<String, Object> propA = new LinkedHashMap<>();
        propA.put("description", descA);
        propA.put("type", "number");

        Map<String, Object> propB = new LinkedHashMap<>();
        propB.put("description", descB);
        propB.put("type", "number");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("a", propA);
        properties.put("b", propB);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("a", "b"));
        return schema;
    }

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
        String template = "你是一个专业的数学计算助手。"
                + "当用户提出数学问题时，你需要："
                + "1. 理解问题中的数学表达式"
                + "2. 使用提供的计算器工具进行计算"
                + "3. 给出清晰的计算过程和结果"
                + "4. 对于复杂表达式，需要分步计算";

        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", template);

        return List.of(systemMessage);
    }

    /**
     * 创建计算器助手 Agent
     *
     * @return Object 数组 [ReActAgent, ReActAgentConfig]
     */
    public static Object[] createCalculatorAgent() {
        Map<String, String> modelCfg = buildModelConfig();

        // 1. 创建 Agent Card
        AgentCard agentCard = new AgentCard("calculator_agent", "数学计算助手，可以进行加减乘除运算");

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
        agentConfig.configureMaxIterations(10);  // 数学题可能需要多步计算

        // 3. 创建 Agent 并应用配置
        ReActAgent agent = new ReActAgent(agentCard);
        agent.configure(agentConfig);

        // 4. 注册计算器工具
        List<LocalFunction> tools = createCalculatorTools();

        // 先注册工具到 Runner.resource_mgr
        Runner.getResourceMgr().addTools(new ArrayList<>(tools), null);

        // 然后添加到 Agent 的 ability_kit
        for (LocalFunction tool : tools) {
            agent.addAbility(tool.getCard());
        }

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
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔍 查询: " + query);
        System.out.println("=".repeat(60));

        Map<String, Object> inputs = Map.of("query", query);
        Object result = agent.invoke(inputs, null).get();

        if (result instanceof Map<?, ?> resultMap) {
            Object output = resultMap.get("output");
            System.out.println("📋 结果: " + (output != null ? output : result));
        } else {
            System.out.println("📋 结果: " + result);
        }
        return result;
    }

    /**
     * 执行多次查询，包含加减乘除运算
     *
     * @param agent ReActAgent 实例
     */
    public static void runMultipleQueries(ReActAgent agent) throws Exception {
        List<String> queries = List.of(
                "计算 100 / 4 + 25 * 2 的结果"
        );

        for (String query : queries) {
            runSingleQuery(agent, query);
            System.out.println();  // 空行分隔
        }
    }

    /**
     * 主函数
     */
    public static void main(String[] args) throws Exception {
        System.out.println("🧮 Calculator Assistant with openJiuwen (ReAct Agent)");
        System.out.println("=".repeat(60));

        // 初始化 Runner（工具注册需要）
        Runner.start();

        try {
            // 1. 创建 Agent
            System.out.println("\n🤖 创建 ReAct Agent...");
            Object[] agentAndConfig = createCalculatorAgent();
            ReActAgent calculatorAgent = (ReActAgent) agentAndConfig[0];

            // 2. 执行多个查询，包含加减乘除运算
            System.out.println("\n🚀 开始执行数学题查询（包含加减乘除）...");
            runMultipleQueries(calculatorAgent);

            System.out.println("\n✅ 完成!");
        } finally {
            // 清理资源
            Runner.stop();
        }
    }
}

