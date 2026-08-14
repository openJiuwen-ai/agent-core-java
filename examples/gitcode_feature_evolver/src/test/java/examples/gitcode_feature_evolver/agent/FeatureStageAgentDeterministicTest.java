/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import examples.gitcode_feature_evolver.job.FeatureFailureCategory;
import examples.gitcode_feature_evolver.job.FeatureStage;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic structured Agent-result parsing checks. */
public final class FeatureStageAgentDeterministicTest {
    private FeatureStageAgentDeterministicTest() {
    }

    /** Run all local Agent-result protocol checks. */
    public static void main(String[] args) throws Exception {
        testJsonModeConfiguration();
        testBoundedLineWriteProtocol();
        testBoundedReadAndSearchProtocol();
        testReadByteBudget();
        testToolErrorVocabulary();
        testModelInputGuard();
        testModelFailureClassificationAndRetry();
        testHarnessInstallation();
        testApprovedGateSchema();
        FeatureStageAgent.Result done = FeatureStageAgent.parseResult(
                FeatureStage.DESIGN, Map.of("result_type", "answer", "output", """
                        {"devflow_result":{"status":"DONE","stage":"DESIGN",
                        "summary":"Design artifacts updated"}}
                        """));
        require(done.status() == FeatureStageAgent.Status.DONE,
                "a valid DONE result was rejected");
        require(done.summary().equals("Design artifacts updated"),
                "the structured summary was not parsed");

        FeatureStageAgent.Result blocked = FeatureStageAgent.parseResult(
                FeatureStage.DESIGN,
                "{\"devflow_result\":{\"status\":\"BLOCKED\","
                        + "\"summary\":\"decision required\",\"failure\":{"
                        + "\"code\":\"PRODUCT_DECISION_REQUIRED\","
                        + "\"requestedInputs\":[\"retention policy\"],"
                        + "\"evidenceSummary\":\"contract is ambiguous\"}}}");
        require(blocked.status() == FeatureStageAgent.Status.BLOCKED
                        && blocked.failure().orElseThrow().requestedInputs()
                        .equals(List.of("retention policy")),
                "a genuine BLOCKED result or its bounded failure claim was not parsed");
        require(FeatureStageAgent.protocolFailure(FeatureStage.DESIGN, blocked).category()
                        == FeatureFailureCategory.PRODUCT_DECISION,
                "the Controller did not classify an allow-listed product blocker");

        FeatureStageAgent.Result needsContext = FeatureStageAgent.parseResult(
                FeatureStage.DESIGN,
                "{\"devflow_result\":{\"status\":\"needs_context\"}}");
        require(needsContext.status() == FeatureStageAgent.Status.NEEDS_CONTEXT,
                "a genuine NEEDS_CONTEXT result was reclassified");

        requireEmptyOutput(null, "a null response was not classified as empty output");
        requireEmptyOutput(Map.of("output", ""),
                "an empty Runner output was not classified as empty output");
        requireInvalidOutput("design completed without a result block",
                "non-JSON output was not classified as invalid output");
        requireInvalidOutput("{\"status\":\"DONE\"}",
                "an omitted devflow_result object was accepted");
        requireInvalidOutput("{\"devflow_result\":{\"status\":\"INVALID_OUTPUT\"}}",
                "an internal controller status was accepted from the Agent");

        System.out.println("FeatureStageAgentDeterministicTest: PASS");
    }

    private static void testApprovedGateSchema() {
        Map<String, Object> schema = FeatureApprovedGateWorkflow.inputSchema();
        require(Boolean.FALSE.equals(schema.get("additionalProperties")),
                "runApprovedGate accepted model-controlled arguments");
        require(schema.get("properties") instanceof Map<?, ?> properties
                        && properties.isEmpty(),
                "runApprovedGate exposed Controller-owned parameters");
    }

    private static void testBoundedLineWriteProtocol() throws Exception {
        Path worktree = Files.createTempDirectory("feature-file-tools-");
        Path existing = worktree.resolve("src/Feature.java");
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "class Feature {\n    int oldValue;\n}\n");
        List<Tool> tools = new FeatureFileTools(
                worktree, List.of("src/"), "deterministic-agent").create();
        Tool write = tool(tools, "writeFile");
        write.invoke(Map.of("path", "src/NewFeature.java",
                "lines", List.of("class NewFeature {", "}")), Map.of());
        require(Files.readString(worktree.resolve("src/NewFeature.java"))
                        .equals("class NewFeature {\n}\n"),
                "writeFile did not reconstruct strict JSON line input");
        Tool replace = tool(tools, "replaceInFile");
        replace.invoke(Map.of("path", "src/Feature.java",
                "oldLines", List.of("    int oldValue;"),
                "newLines", List.of("    int newValue;")), Map.of());
        require(Files.readString(existing).contains("int newValue;")
                        && !Files.readString(existing).contains("int oldValue;"),
                "replaceInFile did not apply one bounded exact replacement");
        Files.write(worktree.resolve("src/Binary.dat"), new byte[]{(byte) 0xC3, (byte) 0x28});
        Files.writeString(worktree.resolve("src/Searchable.txt"), "controller-owned status\n");
        Object searchResult = tool(tools, "searchFiles").invoke(
                Map.of("path", "src", "query", "controller-owned"), Map.of());
        require(searchResult instanceof Map<?, ?> result
                        && String.valueOf(result.get("matches")).contains("Searchable.txt")
                        && Integer.valueOf(1).equals(
                        result(result.get("skippedFiles")).get("nonUtf8"))
                        && Boolean.FALSE.equals(result.get("scanComplete")),
                "one non-UTF-8 repository asset aborted all readable search results");
    }

    private static void testBoundedReadAndSearchProtocol() throws Exception {
        Path worktree = Files.createTempDirectory("feature-file-pages-");
        Path source = worktree.resolve("src/Large.txt");
        Files.createDirectories(source.getParent());
        List<String> lines = new ArrayList<>();
        for (int index = 1; index <= 2_105; index++) {
            lines.add("line-" + index);
        }
        Files.write(source, lines);
        List<Tool> tools = new FeatureFileTools(worktree, List.of(), "page-agent").create();
        Map<?, ?> first = result(tool(tools, "readFile").invoke(
                Map.of("path", "src/Large.txt"), Map.of()));
        require(Boolean.TRUE.equals(first.get("hasMore"))
                        && Integer.valueOf(2_001).equals(first.get("nextOffset"))
                        && Integer.valueOf(2_105).equals(first.get("totalLines")),
                "readFile did not expose a stable continuation offset");
        require(String.valueOf(first.get("content")).startsWith("line-1\n")
                        && String.valueOf(first.get("content")).endsWith("line-2000"),
                "readFile returned the wrong first page");
        Map<?, ?> second = result(tool(tools, "readFile").invoke(
                Map.of("path", "src/Large.txt", "offset", 2_001, "limit", 200), Map.of()));
        require(!Boolean.TRUE.equals(second.get("hasMore"))
                        && String.valueOf(second.get("content")).startsWith("line-2001\n"),
                "readFile did not resume from nextOffset");

        Files.writeString(worktree.resolve("src/LongLine.txt"), "x".repeat(3_000));
        Map<?, ?> longLine = result(tool(tools, "readFile").invoke(
                Map.of("path", "src/LongLine.txt"), Map.of()));
        require(Boolean.TRUE.equals(longLine.get("truncated"))
                        && String.valueOf(longLine.get("content")).length() == 2_000,
                "readFile did not bound one pathological line");

        Files.writeString(worktree.resolve("src/Matches.txt"),
                "needle one\nneedle two\nneedle three\n");
        Tool search = tool(tools, "searchFiles");
        Map<?, ?> searchFirst = result(search.invoke(Map.of(
                "path", "src", "query", "needle", "limit", 1), Map.of()));
        require(Boolean.TRUE.equals(searchFirst.get("hasMore"))
                        && Integer.valueOf(1).equals(searchFirst.get("nextOffset"))
                        && Integer.valueOf(3).equals(searchFirst.get("totalMatches")),
                "searchFiles did not publish a continuation offset");
        Map<?, ?> searchSecond = result(search.invoke(Map.of(
                "path", "src", "query", "needle", "offset", 1, "limit", 1), Map.of()));
        require(String.valueOf(searchSecond.get("matches")).contains("needle two"),
                "searchFiles did not resume from its match offset");
        Map<?, ?> fileSearch = result(search.invoke(Map.of(
                "path", "src/Matches.txt", "query", "needle three"), Map.of()));
        require(Integer.valueOf(1).equals(fileSearch.get("totalMatches"))
                        && Integer.valueOf(1).equals(fileSearch.get("scannedFiles")),
                "searchFiles did not accept an exact file path");
    }

    private static void testModelInputGuard() {
        String originalContent = "a".repeat(9_000);
        ToolMessage original = new ToolMessage(originalContent, "call-1", "readFile");
        AssistantMessage nextCall = AssistantMessage.builder().content("")
                .toolCalls(List.of(ToolCall.builder().id("call-2")
                        .name("searchFiles").arguments("{}").build())).build();
        ToolMessage recent = new ToolMessage("recent result", "call-2", "searchFiles");
        ModelCallInputs inputs = ModelCallInputs.builder()
                .messages(new ArrayList<>(List.of(original, nextCall, recent))).build();
        AgentCallbackContext context = AgentCallbackContext.builder().inputs(inputs).build();
        new FeatureModelReliabilityRail("deepseek-chat").beforeModelCall(context);
        ToolMessage bounded = (ToolMessage) inputs.getMessages().get(0);
        require(original.getContentAsString().equals(originalContent),
                "model input pruning mutated the retained original tool result");
        require(!bounded.getContentAsString().equals(originalContent)
                        && bounded.getContentAsString().contains("code points omitted"),
                "oversized tool output was not pruned for the model");
        require(inputs.getMessages().get(2) == recent,
                "the current tool result was pruned before the model could consume it");
        require(FeatureAgentHarness.contextPressureTokens("deepseek-chat")
                        == (int) Math.floor(65_536 * 0.8),
                "context compaction is not triggered before the model limit");
        require(FeatureAgentHarness.MODEL_TIMEOUT_SECONDS == 300.0,
                "the finite model timeout changed unexpectedly");
    }

    private static void testReadByteBudget() throws Exception {
        Path worktree = Files.createTempDirectory("feature-file-byte-budget-");
        Path source = worktree.resolve("Wide.txt");
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            lines.add("界".repeat(2_000));
        }
        Files.write(source, lines);
        Tool read = tool(new FeatureFileTools(
                worktree, List.of(), "byte-agent").create(), "readFile");
        Map<?, ?> page = result(read.invoke(Map.of("path", "Wide.txt"), Map.of()));
        int bytes = String.valueOf(page.get("content")).getBytes(StandardCharsets.UTF_8).length;
        int completeBytes = String.valueOf(page).getBytes(StandardCharsets.UTF_8).length;
        require(bytes <= 40 * 1_024 && completeBytes <= 50 * 1_024
                        && Boolean.TRUE.equals(page.get("hasMore")),
                "readFile exceeded its model-visible byte budget");
    }

    private static void testToolErrorVocabulary() throws Exception {
        Path worktree = Files.createTempDirectory("feature-tool-errors-");
        Files.write(worktree.resolve("binary.dat"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.writeString(worktree.resolve("one-line.txt"), "one line\n");
        List<Tool> tools = new FeatureFileTools(worktree, List.of(), "error-agent").create();
        Tool read = tool(tools, "readFile");
        requireToolFailure(read, Map.of("path", "binary.dat"),
                FeatureToolException.Code.FILE_NOT_UTF8);
        requireToolFailure(read, Map.of("path", "missing.txt"),
                FeatureToolException.Code.PATH_NOT_FOUND);
        requireToolFailure(read, Map.of("path", "one-line.txt", "offset", 2),
                FeatureToolException.Code.OFFSET_OUT_OF_RANGE);
        Tool search = tool(tools, "searchFiles");
        requireToolFailure(search, Map.of("path", "one-line.txt", "query", "x".repeat(201)),
                FeatureToolException.Code.INVALID_ARGUMENT);
    }

    private static void requireToolFailure(Tool tool, Map<String, Object> arguments,
                                           FeatureToolException.Code expected) throws Exception {
        try {
            tool.invoke(arguments, Map.of());
            throw new AssertionError("tool call unexpectedly succeeded for " + expected);
        } catch (FeatureToolException ex) {
            require(ex.code() == expected,
                    "tool failure code was " + ex.code() + " instead of " + expected);
        }
    }

    private static void testModelFailureClassificationAndRetry() {
        require(FeatureGuardedModel.isEmptyResponse(new AssistantMessage(" ")),
                "blank model content was not classified as empty");
        AssistantMessage toolCall = AssistantMessage.builder().content("")
                .toolCalls(List.of(ToolCall.builder().id("call-1")
                        .name("readFile").arguments("{}").build())).build();
        require(!FeatureGuardedModel.isEmptyResponse(toolCall),
                "a valid tool call was classified as an empty model response");
        require(FeatureModelReliabilityRail.classify(new SocketTimeoutException())
                        == FeatureModelReliabilityRail.FailureKind.TIMEOUT,
                "socket timeout was not classified as transient");
        require(FeatureModelReliabilityRail.classify(
                        new IllegalStateException("HTTP status 503"))
                        == FeatureModelReliabilityRail.FailureKind.SERVER,
                "HTTP 5xx was not classified as transient");
        require(FeatureModelReliabilityRail.classify(
                        new IllegalStateException("maximum context length exceeded"))
                        == FeatureModelReliabilityRail.FailureKind.CONTEXT_OVERFLOW,
                "context overflow was not classified for compaction");
        require(FeatureModelReliabilityRail.classify(
                        new IllegalStateException("HTTP status 401"))
                        == FeatureModelReliabilityRail.FailureKind.OTHER,
                "an authentication failure was incorrectly made retryable");

        FeatureModelReliabilityRail rail = new FeatureModelReliabilityRail("deepseek-chat");
        AgentCallbackContext retry = AgentCallbackContext.builder()
                .exception(new FeatureGuardedModel.EmptyModelResponseException())
                .retryAttempt(0).build();
        rail.onModelException(retry);
        require(retry.consumeRetryRequest() != null,
                "empty model response did not request a bounded retry");
        AgentCallbackContext exhausted = AgentCallbackContext.builder()
                .exception(new SocketTimeoutException()).retryAttempt(2).build();
        rail.onModelException(exhausted);
        require(exhausted.consumeRetryRequest() == null,
                "model retry exceeded its configured attempt budget");
    }

    private static void testHarnessInstallation() {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("harness-test").name("harness-test").build());
        ReActAgentConfig configuration = ReActAgentConfig.builder().build()
                .configureModelClient("OpenAI", "test-key", "https://example.invalid/v1",
                        "deepseek-chat", true)
                .configureContextEngine(null, null, false);
        FeatureStageAgent.configureModelRequest(configuration.getModelConfigObj());
        FeatureModelReliabilityRail rail = FeatureAgentHarness.install(agent, configuration);
        require(configuration.getModelClientConfig().getTimeout() == 300.0,
                "the harness did not install its model timeout");
        require(configuration.getModelClientConfig().getMaxRetries() == 0,
                "provider retries could multiply harness retry attempts");
        require(configuration.getContextEngineConfig().getDefaultWindowRoundNum() == null,
                "a ten-round window bypassed pressure-based compaction");
        require(configuration.getContextProcessors() != null
                        && configuration.getContextProcessors().size() == 1,
                "the full-context compactor was not installed");
        require(agent.peekLlm() instanceof FeatureGuardedModel,
                "the empty-response guard was not installed");
        agent.unregisterRail(rail);
    }

    private static Map<?, ?> result(Object value) {
        if (!(value instanceof Map<?, ?> result)) {
            throw new AssertionError("tool result was not a map");
        }
        return result;
    }

    private static Tool tool(List<Tool> tools, String name) {
        return tools.stream().filter(tool -> name.equals(tool.getCard().getName()))
                .findFirst().orElseThrow();
    }

    private static void testJsonModeConfiguration() {
        ModelRequestConfig request = new ModelRequestConfig();
        FeatureStageAgent.configureModelRequest(request);
        require(Integer.valueOf(8192).equals(request.getMaxTokens()),
                "the JSON response token budget changed");
        require(Map.of("type", "json_object").equals(
                        request.getExtraFields().get("response_format")),
                "DeepSeek-compatible JSON mode was not configured");
    }

    private static void requireInvalidOutput(Object response, String failureMessage) {
        FeatureStageAgent.Result result = FeatureStageAgent.parseResult(
                FeatureStage.DESIGN, response);
        require(result.status() == FeatureStageAgent.Status.INVALID_OUTPUT, failureMessage);
    }

    private static void requireEmptyOutput(Object response, String failureMessage) {
        FeatureStageAgent.Result result = FeatureStageAgent.parseResult(
                FeatureStage.DESIGN, response);
        require(result.status() == FeatureStageAgent.Status.EMPTY_OUTPUT, failureMessage);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
