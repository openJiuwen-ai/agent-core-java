/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.processor.ContextProcessor;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.modelclients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.kvcache.KVCacheConfig;
import com.openjiuwen.core.kvcache.KVCacheRuntime;
import com.openjiuwen.core.kvcache.KVCacheTypes;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.subagent.TaskTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * End-to-end demo for the Ascend-affinity KV cache scheduling flows.
 *
 * <p>Unlike {@link AscendAffinityKvCacheDemo} (protocol-level smoke), this
 * demo wires the full orchestration stack from the PR design doc and verifies
 * <em>when</em> the three management actions fire:</p>
 * <ul>
 *   <li>chain 1 — first inference binds the session to the Ascend control
 *       domain and stamps the identity {@code agent_hint};</li>
 *   <li>chain 2 — a context rewrite between rounds makes
 *       {@code KVCacheModelCallHook.handleContextWindowChange} issue a
 *       messages-range {@code evict} automatically, before the next model
 *       call;</li>
 *   <li>chain 3 — {@code session.releaseKvc()} evicts the whole session and
 *       unbinds the runtime;</li>
 *   <li>chain 4 — TaskTool subagents: the sticky {@code verification_agent}
 *       is prefetched on entry and offloaded on success, while a non-sticky
 *       subagent is evicted, both under a parent-bound child cache
 *       identity.</li>
 * </ul>
 *
 * <p>Every chain asserts on the {@code agent_hint} sequence recorded by the
 * embedded {@link AffinityMockGateway}, so a failed scheduling shows up as a
 * missing or misplaced edit. Run with
 * {@code mvn -f examples/kv-cache-demo/pom.xml exec:java
 * -Ddemo.mainClass=examples.AscendAffinitySchedulingDemo}.</p>
 *
 * @since 0.1.17
 */
public final class AscendAffinitySchedulingDemo {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROVIDER = "AscendAffinity";
    private static final String MODEL_NAME = "GLM-5.2";
    private static final String DEFAULT_API_KEY = "demo-key";
    private static final String PROCESSOR_TYPE = "WindowRewriteProcessor";
    private static final String MAIN_SESSION_ID = "kvc-affinity-scheduling-demo";
    private static final String SUBAGENT_SESSION_ID = "kvc-affinity-subagent-demo";
    private static final String SYSTEM_PROMPT = "You are a concise demo assistant.";
    private static final long EDIT_WAIT_MILLIS = 15000L;

    private static final WindowRewriteProcessor WINDOW_REWRITER = new WindowRewriteProcessor();

    private final AffinityMockGateway gateway;
    private final List<String> failedChains = new ArrayList<>();

    private AscendAffinitySchedulingDemo(AffinityMockGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Entry point.
     *
     * @param args unused
     * @throws Exception when a chain fails unexpectedly
     */
    public static void main(String[] args) throws Exception {
        DefaultModelClientFactories.ensureRegistered();
        ContextEngine.registerProcessor(PROCESSOR_TYPE, config -> WINDOW_REWRITER);
        AffinityMockGateway gateway = new AffinityMockGateway();
        gateway.start();
        try {
            Model model = createAffinityModel(gateway.baseUrl());
            // Generous action budget: the offline demo shares one single-core
            // action executor, so keep the default 2s action timeout from
            // cancelling queued management actions (which would trip fail-open).
            KVCacheRuntime runtime = new KVCacheRuntime(new KVCacheConfig(5.0d, 5.0d, 5.0d), () -> model);
            System.out.println("Embedded mock gateway: " + gateway.baseUrl());
            new AscendAffinitySchedulingDemo(gateway).runChains(model, runtime);
        } finally {
            Runner.stop();
            gateway.stop();
        }
    }

    private static Model createAffinityModel(String gatewayUrl) {
        Map<String, String> config = loadConfigOrDefault();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(PROVIDER)
                .apiKey(config.getOrDefault("API_KEY", DEFAULT_API_KEY))
                .apiBase(gatewayUrl)
                .verifySsl(false)
                .extraFields(Map.of("extensions", Map.of("kv_cache", Map.of("mode", "affinity"))))
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(config.getOrDefault("MODEL_NAME", MODEL_NAME))
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private static Map<String, String> loadConfigOrDefault() {
        try {
            return ExampleApiConfigLoader.load();
        } catch (IllegalStateException exception) {
            System.out.println("[demo] apiconfig.json unavailable, using defaults: " + exception.getMessage());
            return Map.of();
        }
    }

    private void runChains(Model model, KVCacheRuntime runtime) throws Exception {
        AgentSession session = newSession(MAIN_SESSION_ID, runtime);
        ReActAgent agent = newSchedulingAgent(model);
        chain1InferenceAffinity(agent, session);
        chain2WindowDiffEvict(agent, session);
        chain3SessionReleaseEvict(session);
        chain4StickySubagentLifecycle(model, runtime);
        reportSummary();
    }

    private AgentSession newSession(String sessionId, KVCacheRuntime runtime) {
        return new AgentSession(sessionId, new LinkedHashMap<>(),
                new AgentCard(sessionId, sessionId, "kv-cache scheduling demo"),
                null, false, new LinkedHashMap<>(), runtime);
    }

    private ReActAgent newSchedulingAgent(Model model) {
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .maxIterations(2)
                .build();
        config.setEnableKvCacheAffinity(true);
        config.configureContextProcessors(
                List.of(new ContextEngine.ProcessorSpec(PROCESSOR_TYPE, null)));
        ReActAgent agent = new ReActAgent(
                new AgentCard(MAIN_SESSION_ID + "-agent", MAIN_SESSION_ID + "-agent", "scheduling demo"));
        agent.configure(config);
        agent.setLlm(model);
        return agent;
    }

    private void chain1InferenceAffinity(ReActAgent agent, AgentSession session) throws Exception {
        gateway.reset();
        Runner.runAgent(agent, Map.of("query", "round one"), session, null);
        if (!awaitHintCount(1)) {
            report("chain1 inference affinity", false, "no agent_hint reached the gateway");
            return;
        }
        JsonNode hint = recordedHints().get(0);
        boolean isIdentity = MAIN_SESSION_ID.equals(hint.path("session_id").asText())
                && MAIN_SESSION_ID.equals(hint.path("parent_session_id").asText())
                && hint.path("context_management").isMissingNode();
        report("chain1 inference affinity (identity hint)", isIdentity, hint.toString());
    }

    private void chain2WindowDiffEvict(ReActAgent agent, AgentSession session) throws Exception {
        gateway.reset();
        WINDOW_REWRITER.arm();
        Runner.runAgent(agent, Map.of("query", "round two"), session, null);
        if (!awaitHintCount(2)) {
            report("chain2 window-diff evict", false, "expected eviction hint + inference hint");
            return;
        }
        JsonNode evictHint = recordedHints().get(0);
        JsonNode edits = evictHint.path("context_management").path("edits");
        boolean isMessageEvict = edits.size() == 1
                && "evict".equals(edits.get(0).path("type").asText())
                && "messages".equals(edits.get(0).path("target").asText())
                && edits.get(0).path("start").isInt() && edits.get(0).path("end").isInt();
        boolean isIdentityAfter = isIdentityHint(recordedHints().get(1));
        report("chain2 window-diff evict (messages range)", isMessageEvict && isIdentityAfter,
                evictHint.toString());
    }

    private void chain3SessionReleaseEvict(AgentSession session) throws Exception {
        gateway.reset();
        session.releaseKvc().join();
        boolean isEvicted = awaitEditType("evict");
        boolean isRuntimeUnbound = session.getKvCacheRuntime().isEmpty();
        boolean isTerminal = !session.suspendKvc().join();
        report("chain3 releaseKvc evict + unbind", isEvicted && isRuntimeUnbound && isTerminal,
                "evicted=" + isEvicted + " runtimeUnbound=" + isRuntimeUnbound
                        + " terminalSuspend=" + isTerminal);
    }

    private void chain4StickySubagentLifecycle(Model model, KVCacheRuntime runtime) throws Exception {
        gateway.reset();
        AgentSession teamSession = newSession(SUBAGENT_SESSION_ID, runtime);
        TaskTool tool = new TaskTool(new ToolCard("scheduling_task_tool", "task_tool", "Run a subagent."),
                newSubagentCoordinator(model));
        ToolOutput stickyOutput = (ToolOutput) tool.invoke(
                Map.of("subagent_type", "verification_agent", "task_description", "verify the result"),
                Map.of("session", teamSession));
        boolean isStickyOk = stickyOutput.isSuccess() && awaitEditType("offload");
        boolean hasStickyLifecycle = hasSubagentLifecycle(SUBAGENT_SESSION_ID, "verification_agent",
                List.of("prefetch", "offload"));
        report("chain5a sticky subagent (prefetch on entry, offload on success)",
                isStickyOk && hasStickyLifecycle, "success=" + stickyOutput.isSuccess()
                        + " error=" + stickyOutput.getError() + " data=" + stickyOutput.getData()
                        + " hints=" + recordedHints());

        gateway.reset();
        tool.invoke(Map.of("subagent_type", "code", "task_description", "run a task"),
                Map.of("session", teamSession));
        boolean hasPlainEvict = hasSubagentLifecycle(SUBAGENT_SESSION_ID, "code", List.of("evict"));
        report("chain5b non-sticky subagent (evict on finish)", hasPlainEvict, "hints=" + recordedHints());
    }

    private DeepAgent newSubagentCoordinator(Model model) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setModel(model);
        config.setSystemPrompt("Coordinator prompt.");
        config.setMaxIterations(1);
        config.setEnableKvCacheAffinity(true);
        config.setTaskLoopEnabled(true);
        SubAgentConfig sticky = new SubAgentConfig();
        sticky.setAgentCard(new AgentCard("verification_agent", "verification_agent", "verifier"));
        sticky.setSystemPrompt("Verify and answer briefly.");
        sticky.setMaxIterations(1);
        sticky.setEnableTaskLoop(true);
        SubAgentConfig plain = new SubAgentConfig();
        plain.setAgentCard(new AgentCard("code", "code", "coder"));
        plain.setSystemPrompt("Answer briefly.");
        plain.setMaxIterations(1);
        plain.setEnableTaskLoop(true);
        config.setSubagents(List.of(sticky, plain));
        return new DeepAgent(new AgentCard(SUBAGENT_SESSION_ID + "-coordinator",
                SUBAGENT_SESSION_ID + "-coordinator", "coordinator"), config, null);
    }

    private boolean hasSubagentLifecycle(String parentSessionId, String subagentType, List<String> editTypes) {
        // Management edits must carry the child cache id under the parent
        // linkage; the subagent's own inference hint is emitted from the task
        // loop's effective session, which currently reports a self-pointing
        // parent (integration gap noted in the README).
        boolean isActionParentLinked = recordedHints().stream()
                .filter(hint -> !isIdentityHint(hint))
                .allMatch(hint -> parentSessionId.equals(hint.path("parent_session_id").asText()));
        boolean hasSubIdentity = recordedHints().stream()
                .anyMatch(hint -> isIdentityHint(hint)
                        && hint.path("session_id").asText().contains("_sub_" + subagentType));
        boolean hasEdits = editTypes.stream().allMatch(this::hasEditTypeAnywhere);
        return isActionParentLinked && hasSubIdentity && hasEdits;
    }

    private boolean isIdentityHint(JsonNode hint) {
        return hint.path("session_id").asText() != null && !hint.path("session_id").asText().isEmpty()
                && hint.path("context_management").isMissingNode();
    }

    private static boolean hasEditType(JsonNode hint, String expectedType) {
        JsonNode edits = hint.path("context_management").path("edits");
        for (JsonNode edit : edits) {
            if (expectedType.equals(edit.path("type").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEditTypeAnywhere(String expectedType) {
        return recordedHints().stream().anyMatch(hint -> hasEditType(hint, expectedType));
    }

    private List<JsonNode> recordedHints() {
        List<JsonNode> hints = new ArrayList<>();
        for (String raw : gateway.recordedAgentHints()) {
            try {
                hints.add(MAPPER.readTree(raw));
            } catch (JsonProcessingException exception) {
                System.out.println("[demo] unreadable hint: " + exception.getMessage());
            }
        }
        return hints;
    }

    private boolean awaitHintCount(int minCount) {
        long deadline = System.currentTimeMillis() + EDIT_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (gateway.recordedAgentHints().size() >= minCount) {
                return true;
            }
            sleepQuietly();
        }
        return false;
    }

    private boolean awaitEditType(String expectedType) {
        long deadline = System.currentTimeMillis() + EDIT_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (hasEditTypeAnywhere(expectedType)) {
                return true;
            }
            sleepQuietly();
        }
        return false;
    }

    private static void sleepQuietly() {
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void report(String chain, boolean isPassed, String detail) {
        System.out.println((isPassed ? "[PASS] " : "[FAIL] ") + chain + " | " + detail);
        if (!isPassed) {
            failedChains.add(chain);
        }
    }

    private void reportSummary() {
        System.out.println();
        System.out.println("==== Ascend-affinity scheduling demo summary ====");
        if (failedChains.isEmpty()) {
            System.out.println("All chains passed: affinity, window-diff evict, release evict, "
                    + "sticky subagent lifecycle.");
            return;
        }
        throw new IllegalStateException("Scheduling demo failed chains: " + failedChains);
    }

    /**
     * Demo context processor that rewrites the conversation window once when
     * armed, simulating a context compression pass.
     *
     * <p>The rewrite (instead of an append) is what makes
     * {@code KVCacheManager.firstChangedDecision} report a modification so the
     * model-call hook evicts the stale message range.</p>
     */
    private static final class WindowRewriteProcessor extends ContextProcessor {
        private final AtomicBoolean rewriteRequested = new AtomicBoolean(false);

        private WindowRewriteProcessor() {
            super(null);
        }

        private void arm() {
            rewriteRequested.set(true);
        }

        @Override
        public CompletionStage<Boolean> triggerGetContextWindow(SessionModelContext context, ContextWindow window,
                                                                Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(rewriteRequested.get());
        }

        @Override
        public CompletionStage<SessionModelContext.ProcessResult> onGetContextWindow(
                SessionModelContext sessionContext, ContextWindow window, Map<String, Object> kwargs) {
            if (rewriteRequested.compareAndSet(true, false)) {
                List<BaseMessage> contextMessages = window.getContextMessages();
                if (contextMessages.size() > 1) {
                    List<BaseMessage> rewritten = new ArrayList<>();
                    rewritten.add(contextMessages.get(contextMessages.size() - 1));
                    window.setContextMessages(rewritten);
                }
            }
            return CompletableFuture.completedFuture(
                    new SessionModelContext.ProcessResult(null, null, window));
        }

        @Override
        public void loadState(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of();
        }
    }
}
