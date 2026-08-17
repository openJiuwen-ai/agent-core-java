/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.deep_agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates DeepAgent tool-call behavior in non-streaming ({@code invoke}) and
 * streaming ({@code stream}) modes. No concurrency — just two sequential calls that
 * each drive the agent to call a registered {@code web_search} tool, then inspect
 * the result / stream to see whether tool-call information is surfaced to the caller.
 *
 * <p>A deterministic fake LLM is used so the example runs <em>without</em> any real
 * model API key or network dependency. The fake model follows a simple protocol:
 * <ul>
 *   <li>user message starting with {@code 搜索:X} → returns a {@code web_search}
 *       tool call with {@code {"query":"X"}};</li>
 *   <li>tool result message → returns a final stop answer summarizing the result.</li>
 * </ul>
 *
 * <p>The example answers two questions:
 * <ol>
 *   <li><b>Non-streaming invoke</b>: does the returned result map contain tool-call
 *       information (tool_calls, messages, stream_chunks, etc.)?</li>
 *   <li><b>Streaming</b>: does the stream contain the tool's output stream — i.e.
 *       {@code llm_output} chunks carrying {@code tool_calls}?</li>
 * </ol>
 *
 * <p>A {@link ToolCallLoggerRail} is registered to independently confirm that the
 * tool was actually called in both modes, even when the result / stream does not
 * expose tool-call details.
 *
 * <p>Run (from the agent-core-java repo root):
 * <pre>
 * mvn -DskipTests compile
 * mvn dependency:copy-dependencies "-DoutputDirectory=target/dependency" "-DincludeScope=test" -q
 * javac -encoding UTF-8 -source 17 -target 17 -cp "target/classes;target/dependency/*" \
 *   -d examples/deep_agent/build examples/deep_agent/DeepAgentToolCallStreamExample.java
 * java "-Dfile.encoding=UTF-8" -cp "examples/deep_agent/build;target/classes;target/dependency/*" \
 *   examples.deep_agent.DeepAgentToolCallStreamExample
 * </pre>
 *
 * @since 0.1.14
 */
public final class DeepAgentToolCallStreamExample {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String WEB_SEARCH_TOOL = "web_search";

    private DeepAgentToolCallStreamExample() {
    }

    /**
     * Entry point.
     *
     * @param args unused
     * @throws Exception if the example fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== DeepAgent 流式工具调用示例（同一会话派生 2 个任务，每个任务 2 次工具调用）===");

        Path workspacePath = Files.createTempDirectory("deep-agent-toolcall-");
        System.out.println("[setup] workspace=" + workspacePath);

        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.start();

        FakeToolModelClient.ensureFactoryRegistered();
        Model fakeModel = FakeToolModelClient.newModel();

        List<ToolCallRecord> toolCallLog = new CopyOnWriteArrayList<>();
        DeepAgent agent = buildAgent(workspacePath, fakeModel, toolCallLog);

        // 同一会话 conversation_id，连续派生 2 个任务（每次 stream 调用 -> 新 handlerRound -> 新 task_id）
        String conversationId = "session_multi_task";
        try {
            // --- 任务 1 ---
            System.out.println();
            System.out.println("========== [任务1] stream（query=搜索:Topic-A，预期 2 次工具调用）==========");
            Map<String, Object> task1Inputs = new LinkedHashMap<>();
            task1Inputs.put("query", "搜索:Topic-A");
            task1Inputs.put("conversation_id", conversationId);
            int logStart1 = toolCallLog.size();
            Iterator<Object> stream1 = agent.stream(task1Inputs);
            inspectStreamResult(stream1, toolCallLog, logStart1, "任务1");

            // --- 任务 2（同一会话，handlerRound 递增 -> 不同 task_id）---
            System.out.println();
            System.out.println("========== [任务2] stream（query=搜索:Topic-B，预期 2 次工具调用）==========");
            Map<String, Object> task2Inputs = new LinkedHashMap<>();
            task2Inputs.put("query", "搜索:Topic-B");
            task2Inputs.put("conversation_id", conversationId);
            int logStart2 = toolCallLog.size();
            Iterator<Object> stream2 = agent.stream(task2Inputs);
            inspectStreamResult(stream2, toolCallLog, logStart2, "任务2");
        } finally {
            try {
                agent.close();
            } catch (Exception ignored) {
                // best-effort
            }
            try {
                CheckpointerFactory.setDefaultCheckpointer(null);
            } catch (Exception ignored) {
                // best-effort
            }
            Runner.stop();
        }

        System.out.println();
        System.out.println("=== 示例结束 ===");
    }

    private static DeepAgent buildAgent(Path workspacePath, Model fakeModel,
            List<ToolCallRecord> toolCallLog) {
        Map<String, Object> modelMap = new LinkedHashMap<>();
        modelMap.put("model", "fake-tool-model");
        modelMap.put("temperature", 0.0);
        modelMap.put("max_tokens", 128);

        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTaskLoop(true)
                .enableTaskPlanning(false)
                .enableTenantIsolation(false)
                .restrictToWorkDir(false)
                .systemPrompt("你是一个搜索助手。根据用户请求调用 web_search 工具，"
                        + "然后用一句话中文总结结果。")
                .maxIterations(8)
                .completionTimeout(120.0)
                .language("cn")
                .model(modelMap)
                .workspacePath(workspacePath.toString())
                .build();

        AgentCard card = AgentCard.builder()
                .name("tool_call_stream_agent")
                .description("DeepAgent 流式/非流式工具调用示例").build();
        Workspace ws = Workspace.builder().rootPath(workspacePath.toString()).language("cn").build();
        DeepAgent agent = HarnessFactory.createDeepAgent(card, config, ws);
        agent.getAgent().setLlm(fakeModel);

        agent.registerHarnessTool(buildWebSearchTool("web_search_tool"));
        agent.getAgent().registerRail(new ToolCallLoggerRail(toolCallLog));
        agent.ensureInitialized();
        return agent;
    }

    private static Tool buildWebSearchTool(String toolId) {
        ToolCard card = ToolCard.builder()
                .id(toolId).name(WEB_SEARCH_TOOL)
                .description("在互联网上搜索信息（模拟流式 HTTP 调用，返回多片段）").build();
        // 注意：被包装的函数返回 StreamingSearchResult（实现了 Iterable<String>）。
        // 这使得 LocalFunction.stream() 会把它当作流式结果返回多个片段；
        // 而 LocalFunction.invoke() 也会把同一个对象返回（toString() 为完整拼接字符串）。
        return new LocalFunction(card, inputs -> {
            String query = readString(inputs.get("query"));
            System.out.println("  -> [web_search] 正在执行流式搜索 query='" + query + "' ...");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<String> chunks = new ArrayList<>();
            chunks.add("正在搜索 '" + query + "'...\n");
            chunks.add("找到 3 条相关结果:\n");
            chunks.add("web_search 结果: 关于 '" + query + "' 检索到 3 条摘要");
            return new StreamingSearchResult(chunks);
        });
    }

    // ---------- Non-streaming result inspection ----------

    private static void inspectInvokeResult(Map<String, Object> result,
            List<ToolCallRecord> toolCallLog, int logStart) {
        System.out.println("--- 非流式 invoke 结果分析 ---");

        System.out.println("原始结果 (JSON):");
        printJsonIndented(result, "  ");

        System.out.println("顶层 keys: " + result.keySet());

        String[] toolRelatedKeys = {"tool_calls", "messages", "stream_chunks",
            "tool_results", "tool_call_ids"};
        System.out.println("工具相关字段检查:");
        for (String key : toolRelatedKeys) {
            Object value = result.get(key);
            System.out.println("  " + key + ": "
                    + (value != null ? "存在 (" + truncate(String.valueOf(value), 80) + ")" : "不存在"));
        }

        Object output = result.get("output");
        System.out.println("output: " + truncate(String.valueOf(output), 200));

        Object roundsObj = result.get("rounds");
        if (roundsObj instanceof List<?> rounds) {
            System.out.println("rounds 数量: " + rounds.size());
            for (int i = 0; i < rounds.size(); i++) {
                if (rounds.get(i) instanceof Map<?, ?> round) {
                    System.out.println("  round[" + i + "] keys: " + round.keySet());
                    System.out.println("  round[" + i + "] output: "
                            + truncate(String.valueOf(round.get("output")), 120));
                    System.out.println("  round[" + i + "] tool_calls: "
                            + (round.get("tool_calls") != null ? "存在" : "不存在"));
                    System.out.println("  round[" + i + "] messages: "
                            + (round.get("messages") != null ? "存在" : "不存在"));
                    System.out.println("  round[" + i + "] stream_chunks: "
                            + (round.get("stream_chunks") != null ? "存在" : "不存在"));
                }
            }
        }

        Object finalResult = result.get("final_result");
        if (finalResult instanceof Map<?, ?> fr) {
            System.out.println("final_result keys: " + fr.keySet());
        }

        System.out.println("Rail 记录的工具调用:");
        for (int i = logStart; i < toolCallLog.size(); i++) {
            ToolCallRecord r = toolCallLog.get(i);
            System.out.println("  [" + r.phase() + "] tool=" + r.tool() + " id=" + r.toolCallId()
                    + " args=" + truncate(r.args(), 60)
                    + ("after".equals(r.phase()) ? " result=" + truncate(r.result(), 60) : ""));
        }

        boolean hasToolCallsInResult = result.get("tool_calls") != null;
        boolean hasToolCallsInRounds = false;
        if (roundsObj instanceof List<?> rounds) {
            for (Object r : rounds) {
                if (r instanceof Map<?, ?> round && round.get("tool_calls") != null) {
                    hasToolCallsInRounds = true;
                    break;
                }
            }
        }
        boolean toolWasCalled = toolCallLog.size() > logStart;

        System.out.println();
        System.out.println("结论:");
        System.out.println("  工具是否被调用: " + (toolWasCalled ? "是（Rail 记录了调用）" : "否"));
        System.out.println("  结果顶层是否含 tool_calls: " + (hasToolCallsInResult ? "是" : "否"));
        System.out.println("  rounds 是否含 tool_calls: " + (hasToolCallsInRounds ? "是" : "否"));
        System.out.println("  => 非流式 invoke 结果"
                + (hasToolCallsInResult || hasToolCallsInRounds ? "包含" : "不包含")
                + "工具调用信息（仅返回最终回答，中间工具调用不外露）");
    }

    // ---------- Streaming result inspection ----------

    private static void inspectStreamResult(Iterator<Object> stream,
            List<ToolCallRecord> toolCallLog, int logStart, String taskLabel) {
        System.out.println("--- [" + taskLabel + "] 流式 stream 结果分析 ---");

        List<Object> items = new ArrayList<>();
        while (stream.hasNext()) {
            items.add(stream.next());
        }

        System.out.println("[" + taskLabel + "] 流中 item 数量: " + items.size());

        boolean hasToolCallChunk = false;
        boolean hasContentChunk = false;
        boolean hasAnswerChunk = false;
        int toolOutputChunkCount = 0;
        int toolCallDecisionCount = 0;
        String firstSessionId = null;
        String firstTaskId = null;
        // 每个 tool_call_id 对应一次工具调用；统计不同的 tool_call_id 数量
        java.util.Set<String> distinctToolCallIds = new java.util.LinkedHashSet<>();

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            System.out.println("  [" + taskLabel + "] chunk[" + i + "] 原始 (JSON):");
            printJsonIndented(item, "    ");

            if (!(item instanceof OutputSchema os)) {
                continue;
            }
            String type = os.getType();
            Object payload = os.getPayload();

            if ("llm_output".equals(type) && payload instanceof Map<?, ?> pm) {
                if (pm.get("tool_calls") != null) {
                    hasToolCallChunk = true;
                    Object tcs = pm.get("tool_calls");
                    if (tcs instanceof List<?> list) {
                        toolCallDecisionCount += list.size();
                        for (Object tc : list) {
                            if (tc instanceof Map<?, ?> tcMap && tcMap.get("id") != null) {
                                distinctToolCallIds.add(String.valueOf(tcMap.get("id")));
                            }
                        }
                    }
                }
                if (pm.get("content") != null) {
                    hasContentChunk = true;
                }
            } else if ("tool_output".equals(type) && payload instanceof Map<?, ?> pm) {
                toolOutputChunkCount++;
                if (firstSessionId == null && pm.get("session_id") != null) {
                    firstSessionId = String.valueOf(pm.get("session_id"));
                }
                if (firstTaskId == null && pm.get("task_id") != null) {
                    firstTaskId = String.valueOf(pm.get("task_id"));
                }
                if (pm.get("tool_call_id") != null) {
                    distinctToolCallIds.add(String.valueOf(pm.get("tool_call_id")));
                }
                System.out.println("    -> [" + taskLabel + "][tool_output] session_id=" + pm.get("session_id")
                        + " task_id=" + pm.get("task_id")
                        + " tool_name=" + pm.get("tool_name")
                        + " tool_call_id=" + pm.get("tool_call_id")
                        + " index=" + os.getIndex()
                        + " content=" + truncate(String.valueOf(pm.get("content")), 80));
            }
            if ("answer".equals(type)) {
                hasAnswerChunk = true;
            }
        }

        System.out.println("[" + taskLabel + "] Rail 记录的工具调用:");
        for (int i = logStart; i < toolCallLog.size(); i++) {
            ToolCallRecord r = toolCallLog.get(i);
            System.out.println("  [" + r.phase() + "] tool=" + r.tool() + " id=" + r.toolCallId()
                    + " args=" + truncate(r.args(), 60)
                    + ("after".equals(r.phase()) ? " result=" + truncate(r.result(), 60) : ""));
        }

        boolean toolWasCalled = toolCallLog.size() > logStart;
        int railAfterCount = 0;
        for (int i = logStart; i < toolCallLog.size(); i++) {
            if ("after".equals(toolCallLog.get(i).phase())) {
                railAfterCount++;
            }
        }

        System.out.println();
        System.out.println("[" + taskLabel + "] 结论:");
        System.out.println("  工具是否被调用: " + (toolWasCalled ? "是（Rail 记录了调用）" : "否"));
        System.out.println("  流中是否含 tool_calls chunk (type=llm_output, LLM 决策): "
                + (hasToolCallChunk ? "是" : "否"));
        System.out.println("  LLM 决策的 tool_calls 数量: " + toolCallDecisionCount);
        System.out.println("  流中是否含 content chunk (type=llm_output, LLM 回答): "
                + (hasContentChunk ? "是" : "否"));
        System.out.println("  流中是否含 answer chunk: " + (hasAnswerChunk ? "是" : "否"));
        System.out.println("  tool_output chunk 数量: " + toolOutputChunkCount);
        System.out.println("  Rail after(工具完成) 记录数: " + railAfterCount);
        System.out.println("  不同 tool_call_id 数量: " + distinctToolCallIds.size());
        if (!distinctToolCallIds.isEmpty()) {
            System.out.println("  tool_call_id 列表: " + distinctToolCallIds);
        }
        System.out.println("  首个 session_id: " + firstSessionId);
        System.out.println("  首个 task_id: " + firstTaskId);
        System.out.println("  => [" + taskLabel + "] 流式 stream "
                + (hasToolCallChunk ? "包含" : "不包含") + " LLM 工具调用决策信息；"
                + (toolOutputChunkCount > 0 ? "包含" : "不包含") + " 工具响应流信息"
                + (toolOutputChunkCount > 0
                        ? "（统一 tool_output 类型，payload 含 "
                        + "session_id/task_id/tool_name/tool_call_id/content，index=chunkIndex）"
                        : "（ReActAgent stream 模式调用 tool.invoke()，不转发 tool.stream()）"));
    }

    // ---------- Helpers ----------

    private static String mapToCompactString(Map<?, ?> map) {
        if (map == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }

    /**
     * 把任意对象序列化为 pretty JSON 字符串；无法序列化时回退到 {@code toString()}。
     */
    private static String toJsonString(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            try {
                return MAPPER.writeValueAsString(MAPPER.valueToTree(obj));
            } catch (Exception e2) {
                return String.valueOf(obj);
            }
        }
    }

    /**
     * 按 JSON pretty 格式打印对象，每行加 {@code indent} 前缀。
     */
    private static void printJsonIndented(Object obj, String indent) {
        String json = toJsonString(obj);
        for (String line : json.split("\n", -1)) {
            System.out.println(indent + line);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replace('\n', ' ').replace('\r', ' ');
        return one.length() <= max ? one : one.substring(0, max) + "...";
    }

    private static String readString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ToolCallRecord(String phase, String tool, String toolCallId,
            String args, String result) {
    }

    /**
     * 流式搜索结果：既可作为 {@link Iterable} 供 {@link LocalFunction#stream} 迭代输出多个片段，
     * 又重写了 {@code toString()} 以便在非流式 invoke 模式下作为 ToolMessage content 时是可读的完整字符串。
     */
    static final class StreamingSearchResult implements Iterable<String> {
        private final List<String> chunks;

        StreamingSearchResult(List<String> chunks) {
            this.chunks = chunks;
        }

        @Override
        public Iterator<String> iterator() {
            return chunks.iterator();
        }

        public int size() {
            return chunks.size();
        }

        public List<String> chunks() {
            return chunks;
        }

        @Override
        public String toString() {
            return String.join("", chunks);
        }
    }

    /**
     * Rail that logs every tool call (before/after) into a thread-safe sink.
     */
    private static final class ToolCallLoggerRail extends AgentRail {
        private final List<ToolCallRecord> sink;

        ToolCallLoggerRail(List<ToolCallRecord> sink) {
            this.sink = sink;
        }

        @Override
        public int getPriority() {
            return 10;
        }

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            record(ctx, "before");
        }

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            record(ctx, "after");
        }

        private void record(AgentCallbackContext ctx, String phase) {
            String tool = "?";
            String args = "";
            String toolCallId = "?";
            String resultSnippet = "";
            String resultKind = "";
            if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                tool = inputs.getToolName() == null ? "?" : inputs.getToolName();
                args = String.valueOf(inputs.getToolArgs());
                if (inputs.getToolCall() != null && inputs.getToolCall().getId() != null) {
                    toolCallId = inputs.getToolCall().getId();
                }
                if ("after".equals(phase) && inputs.getToolResult() != null) {
                    Object res = inputs.getToolResult();
                    if (res instanceof StreamingSearchResult ssr) {
                        resultKind = "[流式结果 " + ssr.size() + " 片段] ";
                        resultSnippet = truncate(String.valueOf(res), 60);
                    } else if (res instanceof Iterable<?> iter) {
                        List<Object> list = new ArrayList<>();
                        iter.forEach(list::add);
                        resultKind = "[流式结果 " + list.size() + " 片段] ";
                        resultSnippet = truncate(String.valueOf(res), 60);
                    } else {
                        resultKind = "[普通结果] ";
                        resultSnippet = truncate(String.valueOf(res), 60);
                    }
                }
            }
            sink.add(new ToolCallRecord(phase, tool, toolCallId,
                    truncate(args, 80), resultKind + resultSnippet));
            System.out.println("  [TOOL][" + phase + "] tool=" + tool + " id=" + toolCallId
                    + ("after".equals(phase) ? " result=" + resultKind + resultSnippet : "")
                    + " args=" + truncate(args, 80));
        }
    }

    /**
     * Deterministic fake LLM that needs no API key or network.
     * <p>It inspects the last message role:
     * <ul>
     *   <li>user message starting with {@code 搜索:X} → returns a {@code web_search}
     *       tool call with {@code {"query":"X"}};</li>
     *   <li>tool result message → returns a final stop answer.</li>
     * </ul>
     */
    static final class FakeToolModelClient extends BaseModelClient {
        private static final String PROVIDER = "fake-tool-stream";
        private static volatile boolean factoryRegistered = false;
        private final AtomicInteger callCounter = new AtomicInteger(0);

        FakeToolModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        static void ensureFactoryRegistered() {
            if (!factoryRegistered) {
                synchronized (FakeToolModelClient.class) {
                    if (!factoryRegistered) {
                        Model.registerFactory(new Model.ModelClientFactory() {
                            @Override
                            public String providerName() {
                                return PROVIDER;
                            }

                            @Override
                            public BaseModelClient create(ModelRequestConfig mc, ModelClientConfig cc) {
                                return new FakeToolModelClient(mc, cc);
                            }
                        });
                        factoryRegistered = true;
                    }
                }
            }
        }

        static Model newModel() {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(PROVIDER).clientId("fake-tool-stream-client")
                    .apiKey("fake-key").apiBase("http://fake-base").timeout(60.0).build();
            return new Model(clientConfig, null);
        }

        @Override
        protected void validateConfig() {
            // no-op: this fake client does not require real api_key / api_base
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) {
            return buildResponse(messages);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                Float topP, String model, Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) {
            AssistantMessage msg = buildResponse(messages);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(msg.getContent()).toolCalls(msg.getToolCalls())
                    .finishReason(msg.getFinishReason()).build();
            return List.of(chunk).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("FakeToolModelClient does not support generateImage");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("FakeToolModelClient does not support generateSpeech");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                String audioUrl, String model, String size, String resolution, int duration,
                boolean promptExtend, boolean watermark, String negativePrompt, Integer seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("FakeToolModelClient does not support generateVideo");
        }

        private AssistantMessage buildResponse(Object messages) {
            JsonNode tree;
            try {
                tree = MAPPER.valueToTree(messages);
            } catch (Exception ignored) {
                tree = null;
            }
            if (tree == null || !tree.isArray() || tree.isEmpty()) {
                return AssistantMessage.builder()
                        .content("请使用 '搜索:X' 格式提出请求。")
                        .finishReason("stop").build();
            }
            // 找到最后一个 user message 作为"本轮"起点，避免跨任务历史污染
            int lastUserIdx = -1;
            for (int i = tree.size() - 1; i >= 0; i--) {
                if ("user".equals(tree.get(i).path("role").asText(""))) {
                    lastUserIdx = i;
                    break;
                }
            }
            if (lastUserIdx < 0) {
                return AssistantMessage.builder()
                        .content("请使用 '搜索:X' 格式提出请求。")
                        .finishReason("stop").build();
            }
            // 统计本轮（lastUserIdx 之后）assistant 发起的 tool_call 次数
            int assistantToolCallCount = 0;
            for (int i = lastUserIdx + 1; i < tree.size(); i++) {
                JsonNode node = tree.get(i);
                if ("assistant".equals(node.path("role").asText(""))) {
                    JsonNode tcs = node.path("tool_calls");
                    if (tcs.isArray() && tcs.size() > 0) {
                        assistantToolCallCount++;
                    }
                }
            }
            JsonNode last = tree.get(tree.size() - 1);
            String lastRole = last.path("role").asText("");
            String lastContent = readContent(last);
            String userQuery = extractQuery(readContent(tree.get(lastUserIdx)));

            if ("user".equals(lastRole)) {
                // 本轮开始，发起第 1 次工具调用
                return buildToolCallResponse(userQuery, 1);
            }
            if ("tool".equals(lastRole)) {
                if (assistantToolCallCount < 2) {
                    // 本轮已发起 1 次工具调用，发起第 2 次（扩展搜索）
                    return buildToolCallResponse("扩展:" + userQuery, 2);
                }
                // 本轮已发起 2 次工具调用，给最终答案
                return buildFinalAnswer(lastContent);
            }
            return AssistantMessage.builder()
                    .content("请使用 '搜索:X' 格式提出请求。")
                    .finishReason("stop").build();
        }

        private static int countToolResults(JsonNode tree) {
            int count = 0;
            for (JsonNode node : tree) {
                if ("tool".equals(node.path("role").asText(""))) {
                    count++;
                }
            }
            return count;
        }

        private static String extractQuery(String userContent) {
            String query = userContent == null ? "" : userContent.trim();
            if (!query.startsWith("搜索:")) {
                return "";
            }
            return query.substring("搜索:".length()).trim();
        }

        private static String extractOriginalUserQuery(JsonNode tree) {
            for (JsonNode node : tree) {
                if ("user".equals(node.path("role").asText(""))) {
                    return extractQuery(readContent(node));
                }
            }
            return "";
        }

        private static String readContent(JsonNode msg) {
            JsonNode contentNode = msg.path("content");
            if (contentNode.isTextual()) {
                return contentNode.asText("");
            }
            if (contentNode.isArray() && contentNode.size() > 0) {
                return contentNode.get(0).path("text").asText(contentNode.get(0).asText(""));
            }
            return "";
        }

        private AssistantMessage buildToolCallResponse(String query, int callSeq) {
            String q = query == null ? "" : query;
            ToolCall toolCall = ToolCall.builder()
                    .id("call_" + callCounter.incrementAndGet() + "_"
                            + UUID.randomUUID().toString().substring(0, 8))
                    .name(WEB_SEARCH_TOOL)
                    .arguments(String.format("{\"query\":\"%s\"}", escape(q)))
                    .index(0).build();
            return AssistantMessage.builder()
                    .content("").toolCalls(List.of(toolCall))
                    .finishReason("tool_calls").build();
        }

        private static AssistantMessage buildFinalAnswer(String toolResult) {
            String summary = toolResult == null ? "" : toolResult;
            if (summary.length() > 120) {
                summary = summary.substring(0, 120) + "...";
            }
            return AssistantMessage.builder()
                    .content("已完成搜索。" + summary)
                    .finishReason("stop").build();
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n");
        }
    }
}
