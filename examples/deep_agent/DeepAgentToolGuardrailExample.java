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
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.security.PermissionConfirmResponse;
import com.openjiuwen.harness.security.PermissionConfirmationRequest;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates the DeepAgent tool guardrail (permission) subsystem end-to-end with a
 * deterministic fake LLM — no API key or network required.
 *
 * <p>The example covers six scenarios that exercise the three-state decision model
 * (ALLOW / ASK / DENY) across both pipelines (command-rule and file-guard):
 * <ol>
 *   <li><b>Scenario 1 (ALLOW)</b> — {@code bash=allow} baseline + curl/rm deny rules;
 *       {@code cat} command executes and returns {@code BASH_OK}.</li>
 *   <li><b>Scenario 2 (DENY)</b> — {@code curl *} deny rule blocks execution; result
 *       contains {@code [PERMISSION_DENIED]}.</li>
 *   <li><b>Scenario 3a (ASK approved)</b> — ASK callback returns {@code approved=true};
 *       the tool executes.</li>
 *   <li><b>Scenario 3b (ASK rejected)</b> — ASK callback returns {@code approved=false};
 *       the tool is skipped and the result contains the rejection feedback.</li>
 *   <li><b>Scenario 4 (file_guard read)</b> — {@code read=allow} path rule; {@code read_file}
 *       executes.</li>
 *   <li><b>Scenario 5 (file_guard write)</b> — {@code write=deny} path rule; {@code write_file}
 *       is blocked.</li>
 * </ol>
 *
 * <p>A single {@code permissions} config is reused for scenarios 1, 2, 4 and 5 (it carries
 * curl/rm deny rules and a file_guard {@code /etc/hosts} entry). A separate ASK config is
 * used for scenarios 3a/3b because the ASK callback must be swapped between approved and
 * rejected variants.
 *
 * <p>Run (from the agent-core-java repo root):
 * <pre>
 * mvn -DskipTests compile
 * mvn dependency:copy-dependencies "-DoutputDirectory=target/dependency" "-DincludeScope=test" -q
 * javac -encoding UTF-8 -source 17 -target 17 -cp "target/classes;target/dependency/*" \
 *   -d examples/deep_agent/build examples/deep_agent/DeepAgentToolGuardrailExample.java
 * java "-Dfile.encoding=UTF-8" -cp "examples/deep_agent/build;target/classes;target/dependency/*" \
 *   examples.deep_agent.DeepAgentToolGuardrailExample
 * </pre>
 *
 * @since 0.1.15
 */
public final class DeepAgentToolGuardrailExample {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String BASH_TOOL = "bash";
    private static final String READ_FILE_TOOL = "read_file";
    private static final String WRITE_FILE_TOOL = "write_file";

    /** Marker the rail injects into tool results when a call is denied. */
    private static final String DENIED_MARKER = "[PERMISSION_DENIED]";
    /** Marker the fake bash tool returns on successful execution. */
    private static final String BASH_OK = "BASH_OK";

    /**
     * Thread-local carrier for the protected path used by file_guard scenarios.
     * The fake LLM reads this to decide which tool to call and with what arguments,
     * keeping the LLM deterministic while letting the test control the path per scenario.
     */
    private static final InheritableThreadLocal<String> PROTECTED_PATH =
            new InheritableThreadLocal<>();

    private DeepAgentToolGuardrailExample() {
    }

    /**
     * Entry point.
     *
     * @param args unused
     * @throws Exception if the example fails
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== DeepAgent 工具护栏示例（ALLOW / DENY / ASK / file_guard）===");

        Path workspacePath = Files.createTempDirectory("deep-agent-guardrail-");
        System.out.println("[setup] workspace=" + workspacePath);

        // The protected path for file_guard scenarios — workspace-relative so the
        // FileGuardChecker can resolve it identically on all platforms.
        String protectedPath = workspacePath.resolve("protected_hosts").toString()
                .replace('\\', '/');

        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        Runner.setConfig(RunnerConfig.DEFAULT);
        Runner.start();

        FakeGuardrailModelClient.ensureFactoryRegistered();

        try {
            // ---------- Scenarios 1, 2, 4, 5: shared permission config ----------
            // bash=allow + curl deny + rm deny + file_guard(protectedPath)
            Map<String, Object> guardrailConfig = buildGuardrailPermissions(protectedPath);
            Model guardrailModel = FakeGuardrailModelClient.newModel();
            DeepAgent guardrailAgent = buildAgent(workspacePath, guardrailModel,
                    guardrailConfig, null);

            try {
                // Scenario 1 — ALLOW: cat command executes
                System.out.println();
                System.out.println("========== Scenario 1 (ALLOW): bash=allow, cat 命令应执行 ==========");
                PROTECTED_PATH.set(protectedPath);
                runScenario(guardrailAgent, "CAT", "session_s1_allow",
                        protectedPath, BASH_OK, DENIED_MARKER);
                PROTECTED_PATH.remove();

                // Scenario 2 — DENY: curl rule blocks execution
                System.out.println();
                System.out.println("========== Scenario 2 (DENY): curl * deny 规则拦截 ==========");
                PROTECTED_PATH.set(protectedPath);
                runScenario(guardrailAgent, "CURL", "session_s2_deny",
                        protectedPath, null, DENIED_MARKER);
                PROTECTED_PATH.remove();

                // Scenario 4 — file_guard read: read_file executes
                System.out.println();
                System.out.println("========== Scenario 4 (file_guard read): read=allow, read_file 执行 ==========");
                PROTECTED_PATH.set(protectedPath);
                runScenario(guardrailAgent, "READ", "session_s4_read",
                        protectedPath, "READ_OK", DENIED_MARKER);
                PROTECTED_PATH.remove();

                // Scenario 5 — file_guard write: write_file blocked
                System.out.println();
                System.out.println("========== Scenario 5 (file_guard write): write=deny, write_file 拦截 ==========");
                PROTECTED_PATH.set(protectedPath);
                runScenario(guardrailAgent, "WRITE", "session_s5_write",
                        protectedPath, null, DENIED_MARKER);
                PROTECTED_PATH.remove();
            } finally {
                try {
                    guardrailAgent.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }

            // ---------- Scenario 3a: ASK approved ----------
            System.out.println();
            System.out.println("========== Scenario 3a (ASK approved): 回调 approved=true, 工具执行 ==========");
            Map<String, Object> askApprovedConfig = buildAskPermissions();
            Model askApprovedModel = FakeGuardrailModelClient.newModel();
            ToolPermissionHost approvedHost = buildHost(workspacePath, true, "");
            DeepAgent askApprovedAgent = buildAgent(workspacePath, askApprovedModel,
                    askApprovedConfig, approvedHost);
            try {
                runScenario(askApprovedAgent, "CAT", "session_s3a_approved",
                        null, BASH_OK, DENIED_MARKER);
            } finally {
                try {
                    askApprovedAgent.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }

            // ---------- Scenario 3b: ASK rejected ----------
            System.out.println();
            System.out.println("========== Scenario 3b (ASK rejected): 回调 approved=false, 工具跳过 ==========");
            Map<String, Object> askRejectedConfig = buildAskPermissions();
            Model askRejectedModel = FakeGuardrailModelClient.newModel();
            String rejectionFeedback = "用户拒绝了此工具调用";
            ToolPermissionHost rejectedHost = buildHost(workspacePath, false, rejectionFeedback);
            DeepAgent askRejectedAgent = buildAgent(workspacePath, askRejectedModel,
                    askRejectedConfig, rejectedHost);
            try {
                runScenario(askRejectedAgent, "CAT", "session_s3b_rejected",
                        null, null, rejectionFeedback);
            } finally {
                try {
                    askRejectedAgent.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        } finally {
            PROTECTED_PATH.remove();
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

    // ===================== Scenario runner =====================

    /**
     * Runs a single scenario and inspects the streamed output.
     *
     * <p>The {@code expectOkMarker} / {@code rejectionMarker} pair drives
     * {@link #printScenarioResult}: when {@code expectOkMarker} is non-null the scenario
     * expects the tool to have executed (result contains that marker); when
     * {@code rejectionMarker} is non-null the scenario expects the tool to have been
     * blocked (result contains that marker). Both can be checked simultaneously — an ASK
     * rejection produces the feedback marker rather than {@code [PERMISSION_DENIED]}.
     *
     * <p>The protected path is passed to the fake LLM via two channels:
     * <ol>
     *   <li>{@link #PROTECTED_PATH} — an {@link InheritableThreadLocal} (primary);</li>
     *   <li>the user query itself, encoded as {@code GUARDRAIL:<KEYWORD>|<PATH>}
     *       (fallback, because the agent's thread pool does not inherit
     *       {@code InheritableThreadLocal} values).</li>
     * </ol>
     *
     * @param agent           the configured DeepAgent
     * @param keyword         trigger keyword for the fake LLM (CAT/CURL/RM/READ/WRITE)
     * @param conversationId  session id
     * @param protectedPath   the protected path for file_guard scenarios (may be null
     *                        for scenarios that don't use file_guard)
     * @param expectOkMarker  non-null when the scenario expects successful execution
     * @param rejectionMarker non-null when the scenario expects a rejection in the result
     */
    private static void runScenario(DeepAgent agent, String keyword, String conversationId,
            String protectedPath, String expectOkMarker, String rejectionMarker) {
        // The fake LLM reads the last user message; we embed the keyword + path.
        String userQuery = "GUARDRAIL:" + keyword
                + (protectedPath != null ? "|" + protectedPath : "");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", userQuery);
        inputs.put("conversation_id", conversationId);

        Iterator<Object> stream = agent.stream(inputs);

        // Collect all chunks and build a result summary
        List<Object> items = new ArrayList<>();
        while (stream.hasNext()) {
            items.add(stream.next());
        }

        // Concatenate all content / tool_output text for marker checking
        StringBuilder resultText = new StringBuilder();
        for (Object item : items) {
            if (!(item instanceof OutputSchema os)) {
                continue;
            }
            String type = os.getType();
            Object payload = os.getPayload();
            if (!(payload instanceof Map<?, ?> pm)) {
                continue;
            }
            if ("tool_output".equals(type)) {
                Object content = pm.get("content");
                if (content != null) {
                    resultText.append(content);
                }
            } else if ("answer".equals(type)) {
                Object content = pm.get("content");
                if (content != null) {
                    resultText.append(content);
                }
            } else if ("llm_output".equals(type)) {
                Object content = pm.get("content");
                if (content != null) {
                    resultText.append(content);
                }
            }
        }

        printScenarioResult(keyword, resultText.toString(), items.size(),
                expectOkMarker, rejectionMarker);
    }

    /**
     * Prints the scenario verdict by checking the result text for the expected markers,
     * not by counting afterToolCall rail invocations.
     */
    private static void printScenarioResult(String keyword, String resultText,
            int chunkCount, String expectOkMarker, String rejectionMarker) {
        System.out.println("  [result] chunk 数量: " + chunkCount);
        System.out.println("  [result] 文本摘要: " + truncate(resultText, 200));

        boolean ok = false;
        boolean rejected = false;
        if (expectOkMarker != null && resultText.contains(expectOkMarker)) {
            ok = true;
        }
        if (rejectionMarker != null && resultText.contains(rejectionMarker)) {
            rejected = true;
        }

        System.out.print("  [verdict] ");
        if (expectOkMarker != null && rejectionMarker != null) {
            // Both markers provided — the scenario expects one of them
            if (ok) {
                System.out.println("PASS — 工具执行成功 (标记: " + expectOkMarker + ")");
            } else if (rejected) {
                System.out.println("PASS — 工具被拦截 (标记: " + rejectionMarker + ")");
            } else {
                System.out.println("FAIL — 既未找到执行标记 (" + expectOkMarker
                        + ") 也未找到拦截标记 (" + rejectionMarker + ")");
            }
        } else if (expectOkMarker != null) {
            if (ok) {
                System.out.println("PASS — 工具执行成功 (标记: " + expectOkMarker + ")");
            } else {
                System.out.println("FAIL — 未找到执行标记 (" + expectOkMarker + ")");
            }
        } else if (rejectionMarker != null) {
            if (rejected) {
                System.out.println("PASS — 工具被拦截 (标记: " + rejectionMarker + ")");
            } else {
                System.out.println("FAIL — 未找到拦截标记 (" + rejectionMarker + ")");
            }
        }
    }

    // ===================== Agent & tool builders =====================

    private static DeepAgent buildAgent(Path workspacePath, Model fakeModel,
            Map<String, Object> permissions, ToolPermissionHost host) {
        Map<String, Object> modelMap = new LinkedHashMap<>();
        modelMap.put("model", "fake-guardrail-model");
        modelMap.put("temperature", 0.0);
        modelMap.put("max_tokens", 128);

        DeepAgentConfig.DeepAgentConfigBuilder configBuilder = DeepAgentConfig.builder()
                .enableTaskLoop(true)
                .enableTaskPlanning(false)
                .enableTenantIsolation(false)
                .restrictToWorkDir(false)
                .systemPrompt("你是一个工具护栏测试助手。根据用户请求调用对应工具。")
                .maxIterations(8)
                .completionTimeout(120.0)
                .language("cn")
                .model(modelMap)
                .workspacePath(workspacePath.toString())
                .permissions(permissions);

        if (host != null) {
            configBuilder.permissionHost(host);
        }

        DeepAgentConfig config = configBuilder.build();

        AgentCard card = AgentCard.builder()
                .name("tool_guardrail_agent")
                .description("DeepAgent 工具护栏示例").build();
        Workspace ws = Workspace.builder().rootPath(workspacePath.toString())
                .language("cn").build();
        DeepAgent agent = HarnessFactory.createDeepAgent(card, config, ws);
        agent.getAgent().setLlm(fakeModel);

        // Register all tools — each is registered through all three layers:
        //   1. agent.registerHarnessTool()  (DeepAgent harness layer)
        //      internally calls Runner.resourceMgr().addTool() and agent.getAbilityManager().add()
        // We call registerHarnessTool which handles all three in one shot.
        agent.registerHarnessTool(buildBashTool("bash_tool"));
        agent.registerHarnessTool(buildReadFileTool("read_file_tool"));
        agent.registerHarnessTool(buildWriteFileTool("write_file_tool"));

        agent.ensureInitialized();
        return agent;
    }

    private static Tool buildBashTool(String toolId) {
        ToolCard card = ToolCard.builder()
                .id(toolId).name(BASH_TOOL)
                .description("执行 bash 命令（模拟）").build();
        return new LocalFunction(card, inputs -> {
            String command = readString(inputs.get("command"));
            System.out.println("  -> [bash] 执行命令: " + command);
            return BASH_OK + ": " + command;
        });
    }

    private static Tool buildReadFileTool(String toolId) {
        ToolCard card = ToolCard.builder()
                .id(toolId).name(READ_FILE_TOOL)
                .description("读取文件内容（模拟）").build();
        return new LocalFunction(card, inputs -> {
            String filePath = readString(inputs.get("file_path"));
            System.out.println("  -> [read_file] 读取: " + filePath);
            return "READ_OK: 文件内容来自 " + filePath;
        });
    }

    private static Tool buildWriteFileTool(String toolId) {
        ToolCard card = ToolCard.builder()
                .id(toolId).name(WRITE_FILE_TOOL)
                .description("写入文件内容（模拟）").build();
        return new LocalFunction(card, inputs -> {
            String filePath = readString(inputs.get("file_path"));
            System.out.println("  -> [write_file] 写入: " + filePath);
            return "WRITE_OK: 已写入 " + filePath;
        });
    }

    // ===================== Permission configs =====================

    /**
     * Shared config for scenarios 1, 2, 4, 5.
     *
     * <p>{@code bash=allow} baseline (so cat passes, but curl/rm rules deny).
     * file_guard protects {@code protectedPath} with read=allow, write=deny.
     */
    private static Map<String, Object> buildGuardrailPermissions(String protectedPath) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("enabled", true);
        permissions.put("schema", "tiered_policy");
        permissions.put("permission_mode", "normal");

        // bash=allow — so only explicit deny rules block it
        permissions.put("tools", Map.of("bash", "allow"));
        permissions.put("defaults", Map.of("*", "allow"));

        // Command-level deny rules
        permissions.put("rules", List.of(
                Map.of("id", "curl_deny", "tools", List.of("bash"),
                        "pattern", "curl *", "action", "deny"),
                Map.of("id", "rm_deny", "tools", List.of("bash"),
                        "pattern", "rm *", "action", "deny")));

        permissions.put("approval_overrides", List.of());

        // File-guard pipeline
        Map<String, Object> fileGuard = new LinkedHashMap<>();
        fileGuard.put("enabled", true);
        fileGuard.put("defaults", Map.of("read", "allow", "write", "allow", "exec", "ask"));
        fileGuard.put("paths", List.of(Map.of(
                "path", protectedPath,
                "read", "allow", "write", "deny", "exec", "deny",
                "match", "prefix")));
        permissions.put("file_guard", fileGuard);

        return permissions;
    }

    /**
     * ASK config for scenarios 3a/3b — {@code bash=ask} so the ASK callback fires.
     */
    private static Map<String, Object> buildAskPermissions() {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("enabled", true);
        permissions.put("schema", "tiered_policy");
        permissions.put("permission_mode", "normal");

        // bash=ask — triggers the confirmation callback
        permissions.put("tools", Map.of("bash", "ask"));
        permissions.put("defaults", Map.of("*", "allow"));
        permissions.put("rules", List.of());
        permissions.put("approval_overrides", List.of());

        return permissions;
    }

    /**
     * Builds a {@link ToolPermissionHost} with a confirmation callback.
     *
     * <p>{@code persistAllow(false)} is used (persistence is disabled). The
     * {@code permissionYamlPath} is intentionally not set.
     *
     * @param workspacePath  workspace root
     * @param approved      whether the callback approves the request
     * @param feedback      rejection feedback (used when {@code approved=false})
     */
    private static ToolPermissionHost buildHost(Path workspacePath,
            boolean approved, String feedback) {
        ToolPermissionHost host = ToolPermissionHost.builder()
                .resolveWorkspaceDir(() -> workspacePath)
                // persistAllow(false) — persistence disabled, no permissionYamlPath
                .build();
        host.setRequestPermissionConfirmationFn(req -> {
            System.out.println("  -> [ASK回调] tool=" + req.getToolName()
                    + " args=" + req.getToolArgs()
                    + " -> approved=" + approved);
            return PermissionConfirmResponse.builder()
                    .approved(approved)
                    .feedback(approved ? "" : feedback)
                    .autoConfirm(false)
                    .persistAllow(false)   // persistence disabled
                    .build();
        });
        return host;
    }

    // ===================== Helpers =====================

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

    // ===================== Fake LLM =====================

    /**
     * Deterministic fake LLM that needs no API key or network.
     *
     * <p>It inspects the last user message for a keyword embedded as
     * {@code GUARDRAIL:<KEYWORD>} and returns the corresponding tool call:
     * <ul>
     *   <li>{@code CAT} → {@code bash} with {@code cat <protectedPath>}</li>
     *   <li>{@code CURL} → {@code bash} with {@code curl http://example.com}</li>
     *   <li>{@code RM}  → {@code bash} with {@code rm -rf /tmp/test}</li>
     *   <li>{@code READ}  → {@code read_file} with {@code file_path=<protectedPath>}</li>
     *   <li>{@code WRITE} → {@code write_file} with {@code file_path=<protectedPath>}</li>
     * </ul>
     * When the last message is a tool result, it returns a final stop answer that
     * echoes the tool result text (so markers like {@code BASH_OK} survive into the
     * stream for inspection).
     */
    static final class FakeGuardrailModelClient extends BaseModelClient {
        private static final String PROVIDER = "fake-guardrail";
        private static volatile boolean factoryRegistered = false;
        private final AtomicInteger callCounter = new AtomicInteger(0);

        FakeGuardrailModelClient(ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        static void ensureFactoryRegistered() {
            if (!factoryRegistered) {
                synchronized (FakeGuardrailModelClient.class) {
                    if (!factoryRegistered) {
                        Model.registerFactory(new Model.ModelClientFactory() {
                            @Override
                            public String providerName() {
                                return PROVIDER;
                            }

                            @Override
                            public BaseModelClient create(ModelRequestConfig mc,
                                    ModelClientConfig cc) {
                                return new FakeGuardrailModelClient(mc, cc);
                            }
                        });
                        factoryRegistered = true;
                    }
                }
            }
        }

        static Model newModel() {
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(PROVIDER).clientId("fake-guardrail-client")
                    .apiKey("fake-key").apiBase("http://fake-base").timeout(60.0).build();
            return new Model(clientConfig, null);
        }

        @Override
        protected void validateConfig() {
            // no-op: this fake client does not require real api_key / api_base
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature,
                Float topP, String model, Integer maxTokens, String stop,
                BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) {
            return buildResponse(messages);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools,
                Float temperature, Float topP, String model, Integer maxTokens,
                String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            AssistantMessage msg = buildResponse(messages);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(msg.getContent()).toolCalls(msg.getToolCalls())
                    .finishReason(msg.getFinishReason()).build();
            return List.of(chunk).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages,
                String model, String size, String negativePrompt, int n,
                boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException(
                    "FakeGuardrailModelClient does not support generateImage");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                String model, String voice, String languageType,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException(
                    "FakeGuardrailModelClient does not support generateSpeech");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages,
                String imgUrl, String audioUrl, String model, String size,
                String resolution, int duration, boolean promptExtend,
                boolean watermark, String negativePrompt, Integer seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException(
                    "FakeGuardrailModelClient does not support generateVideo");
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
                        .content("请使用 'GUARDRAIL:<KEYWORD>' 格式提出请求。")
                        .finishReason("stop").build();
            }

            // Find the last user message as the "round" starting point
            int lastUserIdx = -1;
            for (int i = tree.size() - 1; i >= 0; i--) {
                if ("user".equals(tree.get(i).path("role").asText(""))) {
                    lastUserIdx = i;
                    break;
                }
            }
            if (lastUserIdx < 0) {
                return AssistantMessage.builder()
                        .content("请使用 'GUARDRAIL:<KEYWORD>' 格式提出请求。")
                        .finishReason("stop").build();
            }

            JsonNode last = tree.get(tree.size() - 1);
            String lastRole = last.path("role").asText("");
            String userContent = readContent(tree.get(lastUserIdx));

            // If the last message is a tool result, give the final answer echoing the result
            if ("tool".equals(lastRole)) {
                String toolResult = readContent(last);
                return AssistantMessage.builder()
                        .content(toolResult)
                        .finishReason("stop").build();
            }

            // The last message is from the user — decide which tool to call
            if ("user".equals(lastRole)) {
                String[] parts = extractKeywordAndPath(userContent);
                String keyword = parts[0];
                // Primary: InheritableThreadLocal; Fallback: path from user query
                String protectedPath = PROTECTED_PATH.get();
                if ((protectedPath == null || protectedPath.isEmpty())
                        && parts[1] != null && !parts[1].isEmpty()) {
                    protectedPath = parts[1];
                }
                if (protectedPath == null || protectedPath.isEmpty()) {
                    protectedPath = "/etc/hosts";
                }
                return buildToolCall(keyword, protectedPath);
            }

            return AssistantMessage.builder()
                    .content("请使用 'GUARDRAIL:<KEYWORD>' 格式提出请求。")
                    .finishReason("stop").build();
        }

        private AssistantMessage buildToolCall(String keyword, String protectedPath) {
            String toolName;
            String args;
            switch (keyword == null ? "" : keyword.toUpperCase()) {
                case "CAT" -> {
                    toolName = BASH_TOOL;
                    args = String.format("{\"command\":\"cat %s\"}",
                            escape(protectedPath));
                }
                case "CURL" -> {
                    toolName = BASH_TOOL;
                    args = "{\"command\":\"curl http://example.com\"}";
                }
                case "RM" -> {
                    toolName = BASH_TOOL;
                    args = "{\"command\":\"rm -rf /tmp/test\"}";
                }
                case "READ" -> {
                    toolName = READ_FILE_TOOL;
                    args = String.format("{\"file_path\":\"%s\"}",
                            escape(protectedPath));
                }
                case "WRITE" -> {
                    toolName = WRITE_FILE_TOOL;
                    args = String.format("{\"file_path\":\"%s\",\"content\":\"test\"}",
                            escape(protectedPath));
                }
                default -> {
                    return AssistantMessage.builder()
                            .content("未识别的关键词: " + keyword
                                    + "。请使用 CAT/CURL/RM/READ/WRITE。")
                            .finishReason("stop").build();
                }
            }
            ToolCall toolCall = ToolCall.builder()
                    .id("call_" + callCounter.incrementAndGet() + "_"
                            + UUID.randomUUID().toString().substring(0, 8))
                    .name(toolName)
                    .arguments(args)
                    .index(0).build();
            return AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(toolCall))
                    .finishReason("tool_calls").build();
        }

        /**
         * Extracts the keyword and optional path from a user message of the form
         * {@code GUARDRAIL:<KEYWORD>} or {@code GUARDRAIL:<KEYWORD>|<PATH>}.
         *
         * @param userContent the user message text
         * @return a two-element array: {@code [keyword, pathOrEmpty]}
         */
        private static String[] extractKeywordAndPath(String userContent) {
            String content = userContent == null ? "" : userContent.trim();
            String prefix = "GUARDRAIL:";
            if (!content.startsWith(prefix)) {
                return new String[]{"", ""};
            }
            String rest = content.substring(prefix.length()).trim();
            int sep = rest.indexOf('|');
            if (sep < 0) {
                return new String[]{rest, ""};
            }
            return new String[]{rest.substring(0, sep).trim(),
                    rest.substring(sep + 1).trim()};
        }

        private static String readContent(JsonNode msg) {
            JsonNode contentNode = msg.path("content");
            if (contentNode.isTextual()) {
                return contentNode.asText("");
            }
            if (contentNode.isArray() && contentNode.size() > 0) {
                return contentNode.get(0).path("text").asText(
                        contentNode.get(0).asText(""));
            }
            return "";
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n");
        }
    }
}
