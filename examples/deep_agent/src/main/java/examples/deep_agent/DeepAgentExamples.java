/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DeepAgent 示例统一入口。
 *
 * <p>本目录下仅此类包含 {@code main} 方法。通过命令行第一个参数选择要运行的示例，
 * 其余参数透传给具体示例的 {@code run(String[])} 方法。
 *
 * <h2>用法</h2>
 * <pre>
 *   java -cp ... examples.deep_agent.DeepAgentExamples &lt;example-name&gt; [extra args...]
 * </pre>
 *
 * <h2>可选示例</h2>
 * <table border="1">
 *   <tr><th>名称</th><th>说明</th><th>需要 LLM</th><th>需要 Redis</th></tr>
 *   <tr><td>{@code template}</td><td>通用模板：Runner.runAgent + runAgentStreaming 两种调用方式</td><td>是</td><td>否</td></tr>
 *   <tr><td>{@code redis}</td><td>Todo 与 Checkpointer 共享同一 Redis 连接</td><td>是</td><td>是</td></tr>
 *   <tr><td>{@code guardrail}</td><td>工具护栏（ALLOW/ASK/DENY/file_guard）端到端，使用 fake LLM</td><td>否</td><td>否</td></tr>
 *   <tr><td>{@code offload}</td><td>Context Offload 卸载/装载端到端：DeepAgent + Runner 真实触发，大输出工具触发卸载、reload 工具装载回注</td><td>是</td><td>可选</td></tr>
 *   <tr><td>{@code list}</td><td>列出所有可选示例</td><td>—</td><td>—</td></tr>
 * </table>
 *
 * <p>未传参数或传 {@code list} 时打印可选示例清单。
 */
public final class DeepAgentExamples {

    /** 示例名 → 描述（用于 list 输出与未知示例提示）。 */
    private static final Map<String, String> EXAMPLES = new LinkedHashMap<>();

    static {
        EXAMPLES.put("template", "通用模板：Runner.runAgent + runAgentStreaming 两种调用方式（需要 LLM）");
        EXAMPLES.put("redis", "Todo 与 Checkpointer 共享同一 Redis 连接（需要 LLM + Redis）");
        EXAMPLES.put("guardrail", "工具护栏 ALLOW/ASK/DENY/file_guard 端到端（fake LLM，无需 API Key）");
        EXAMPLES.put("offload", "Context Offload 卸载/装载端到端：DeepAgent + Runner 真实触发（需要 LLM；Redis 可选）");
    }

    private DeepAgentExamples() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0]) || "-h".equals(args[0])
                || "--help".equals(args[0])) {
            printUsage();
            return;
        }

        String name = args[0];
        // 透传给具体示例的参数：去掉第一个元素。
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        try {
            switch (name.toLowerCase()) {
                case "template" -> DeepAgentTemplateExample.run(rest);
                case "redis" -> DeepAgentRedisExample.run(rest);
                case "guardrail" -> DeepAgentToolGuardrailExample.run(rest);
                case "offload" -> ContextOffloadExample.run(rest);
                default -> {
                    System.err.println("未知示例: " + name);
                    System.err.println();
                    printUsage();
                    System.exit(2);
                }
            }
        } catch (Exception e) {
            System.err.println("示例执行失败 [" + name + "]: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("DeepAgent 示例统一入口");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  java -cp ... examples.deep_agent.DeepAgentExamples <example-name> [extra args...]");
        System.out.println();
        System.out.println("可选示例:");
        for (Map.Entry<String, String> entry : EXAMPLES.entrySet()) {
            System.out.println("  " + entry.getKey() + "  —  " + entry.getValue());
        }
        System.out.println("  list  —  列出所有可选示例");
    }
}
