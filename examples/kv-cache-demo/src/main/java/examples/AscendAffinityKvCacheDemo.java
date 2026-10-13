/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.modelclients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.kvcache.AgentHint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Offline demo for the Ascend-affinity KV cache scheduling protocol.
 *
 * <p>Runs the main line of the feature: affinity capability declaration,
 * identity {@code agent_hint} carried on a normal inference call, then the
 * prefetch (预取), offload (卸载) and evict (驱逐) management actions with
 * their wire-level {@code context_management.edits}.</p>
 *
 * <p>By default an embedded mock gateway serves the OpenAI-compatible
 * endpoint so the demo runs without any external service; point it at a real
 * Ascend-affinity gateway with
 * {@code -Dopenjiuwen.example.affinityGateway=http://host:port} (hint-content
 * assertions need the embedded mock because only it records the hints).</p>
 *
 * <p>Run with:
 * {@code mvn -f examples/kv-cache-demo/pom.xml compile exec:java
 * -Dexec.mainClass=examples.AscendAffinityKvCacheDemo}</p>
 *
 * @since 0.1.16
 */
public final class AscendAffinityKvCacheDemo {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GATEWAY_PROPERTY = "openjiuwen.example.affinityGateway";
    private static final String PROVIDER = "AscendAffinity";
    private static final String DEFAULT_MODEL_NAME = "GLM-5.2";
    private static final String DEFAULT_API_KEY = "demo-key";
    private static final String SESSION_ID = "s-1";
    private static final String PARENT_SESSION_ID = "root-1";

    private final Model model;
    private final AffinityMockGateway gateway;
    private final List<String> failedScenarios = new ArrayList<>();

    private AscendAffinityKvCacheDemo(Model model, AffinityMockGateway gateway) {
        this.model = model;
        this.gateway = gateway;
    }

    /**
     * Entry point.
     *
     * @param args unused
     * @throws Exception when the model call or gateway fails unexpectedly
     */
    public static void main(String[] args) throws Exception {
        AffinityMockGateway gateway = null;
        try {
            DefaultModelClientFactories.ensureRegistered();
            String gatewayUrl = System.getProperty(GATEWAY_PROPERTY);
            boolean isEmbedded = gatewayUrl == null || gatewayUrl.isBlank();
            if (isEmbedded) {
                gateway = new AffinityMockGateway();
                gateway.start();
                gatewayUrl = gateway.baseUrl();
                System.out.println("Embedded mock gateway: " + gatewayUrl);
            } else {
                System.out.println("External affinity gateway: " + gatewayUrl);
            }
            AscendAffinityKvCacheDemo demo = new AscendAffinityKvCacheDemo(createModel(gatewayUrl), gateway);
            demo.run();
            demo.reportSummary();
        } finally {
            if (gateway != null) {
                gateway.stop();
            }
        }
    }

    private static Model createModel(String gatewayUrl) {
        Map<String, String> config = loadConfigOrDefault();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(PROVIDER)
                .apiKey(config.getOrDefault("API_KEY", DEFAULT_API_KEY))
                .apiBase(gatewayUrl)
                .verifySsl(false)
                .extraFields(Map.of("extensions", Map.of("kv_cache", Map.of("mode", "affinity"))))
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(config.getOrDefault("MODEL_NAME", DEFAULT_MODEL_NAME))
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

    private void run() throws Exception {
        scenario0DeclareCapability();
        scenario1IdentityHintOnInference();
        scenario2Prefetch();
        scenario3Offload();
        scenario4Evict();
    }

    private void scenario0DeclareCapability() {
        boolean isSupported = model.supportsKvCacheAffinity();
        report("scenario0 affinity capability declared", isSupported, "supportsKvCacheAffinity=" + isSupported);
    }

    private void scenario1IdentityHintOnInference() throws Exception {
        model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null, null,
                model.buildKvCacheAffinityInvokeKwargs(SESSION_ID, PARENT_SESSION_ID));
        if (gateway == null) {
            report("scenario1 inference call (hint assertions need embedded mock)", true, "external gateway mode");
            return;
        }
        Optional<JsonNode> hint = lastRecordedHint();
        boolean isIdentified = hint.isPresent()
                && SESSION_ID.equals(hint.get().path("session_id").asText())
                && PARENT_SESSION_ID.equals(hint.get().path("parent_session_id").asText());
        report("scenario1 identity agent_hint on inference", isIdentified, hint.map(JsonNode::toString).orElse(""));
    }

    private void scenario2Prefetch() {
        boolean isSucceeded = model.prefetchKvc(SESSION_ID, PARENT_SESSION_ID).join();
        if (gateway == null) {
            report("scenario2 prefetch (预取) action (return value only)", isSucceeded, "external gateway mode");
            return;
        }
        report("scenario2 prefetch (预取) action", isSucceeded && hasSingleEdit(lastRecordedHint(), "prefetch"),
                lastHintDetail());
    }

    private void scenario3Offload() {
        boolean isSucceeded = model.offloadKvc(SESSION_ID, PARENT_SESSION_ID).join();
        if (gateway == null) {
            report("scenario3 offload (卸载) action (return value only)", isSucceeded, "external gateway mode");
            return;
        }
        report("scenario3 offload (卸载) action", isSucceeded && hasSingleEdit(lastRecordedHint(), "offload"),
                lastHintDetail());
    }

    private void scenario4Evict() {
        AgentHint.KvCacheRange messagesRange = new AgentHint.KvCacheRange("messages", 2, 5, 1, 3, true);
        boolean isRangeSucceeded = model.evictKvc(SESSION_ID, PARENT_SESSION_ID, messagesRange).join();
        if (gateway == null) {
            report("scenario4a evict (驱逐) range (return value only)", isRangeSucceeded, "external gateway mode");
        } else {
            boolean isRangeMatched = isRangeSucceeded && hasMessagesToolsEdits(lastRecordedHint(), "evict");
            report("scenario4a evict (驱逐) messages[2,5)+tools[1,3)", isRangeMatched, lastHintDetail());
        }

        boolean isSessionSucceeded = model.evictKvc(SESSION_ID, PARENT_SESSION_ID, sessionRange()).join();
        if (gateway == null) {
            report("scenario4b evict (驱逐) session (return value only)", isSessionSucceeded, "external gateway mode");
            return;
        }
        boolean isSessionMatched = isSessionSucceeded && hasSingleEdit(lastRecordedHint(), "evict");
        report("scenario4b evict (驱逐) whole session", isSessionMatched, lastHintDetail());
    }

    private static AgentHint.KvCacheRange sessionRange() {
        return new AgentHint.KvCacheRange("session", null, null, null, null, false);
    }

    private Optional<JsonNode> lastRecordedHint() {
        if (gateway == null) {
            return Optional.empty();
        }
        List<String> hints = gateway.recordedAgentHints();
        if (hints.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readTree(hints.get(hints.size() - 1)));
        } catch (JsonProcessingException exception) {
            System.out.println("[demo] unreadable hint: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private String lastHintDetail() {
        return lastRecordedHint().map(JsonNode::toString).orElse("no hint recorded");
    }

    private boolean hasSingleEdit(Optional<JsonNode> hint, String expectedAction) {
        JsonNode edits = editsOf(hint);
        return edits.size() == 1 && isEdit(edits.get(0), expectedAction, "session");
    }

    private boolean hasMessagesToolsEdits(Optional<JsonNode> hint, String expectedAction) {
        JsonNode edits = editsOf(hint);
        return edits.size() == 2
                && isEdit(edits.get(0), expectedAction, "messages")
                && isEdit(edits.get(1), expectedAction, "tools");
    }

    private static JsonNode editsOf(Optional<JsonNode> hint) {
        return hint.map(node -> node.path("context_management").path("edits"))
                .orElseGet(MAPPER::createObjectNode);
    }

    private static boolean isEdit(JsonNode edit, String expectedAction, String expectedTarget) {
        return expectedAction.equals(edit.path("type").asText())
                && expectedTarget.equals(edit.path("target").asText());
    }

    private void report(String scenario, boolean isPassed, String detail) {
        System.out.println((isPassed ? "[PASS] " : "[FAIL] ") + scenario + " | " + detail);
        if (!isPassed) {
            failedScenarios.add(scenario);
        }
    }

    private void reportSummary() {
        System.out.println();
        System.out.println("==== Ascend-affinity demo summary ====");
        if (failedScenarios.isEmpty()) {
            System.out.println("All scenarios passed: capability, identity hint, prefetch, offload, evict.");
            return;
        }
        throw new IllegalStateException("Affinity demo failed scenarios: " + failedScenarios);
    }
}
