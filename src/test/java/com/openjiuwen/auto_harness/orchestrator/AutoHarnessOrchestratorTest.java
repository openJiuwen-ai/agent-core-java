/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.orchestrator;

import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.core.single_agent.rail.AgentRail;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code AutoHarnessOrchestrator} in
 * {@code openjiuwen/auto_harness/orchestrator.py}.
 *
 * <p>Mirrors Python's orchestrator unit tests in
 * {@code tests/unit_tests/auto_harness/test_orchestrator.py}.</p>
 */
class AutoHarnessOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void constructorInitializesRuntimeArtifactsAndDefaultPipelines() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());

        assertThat(orchestrator.getArtifacts()).isNotNull();
        assertThat(orchestrator.getRuntime().getCurrentWorkspace()).isEqualTo("workspace-a");
        assertThat(orchestrator.getRuntime().isConfigBootstrapped()).isTrue();
        assertThat(orchestrator.getRuntime().getSuggestedLocalRepo()).isEqualTo("repo-a");
        assertThat(orchestrator.getPipelineRegistry().names()).containsExactly(
                AutoHarnessPipelineNames.META_EVOLVE_PIPELINE,
                AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE
        );
        assertThat(Path.of(orchestrator.getPaths().getRuntimeExtensionsDir())).isDirectory();
    }

    @Test
    void factoryBuildsOrchestratorWithProvidedConfig() {
        AutoHarnessConfig config = config();

        AutoHarnessOrchestrator orchestrator = AutoHarnessOrchestrator.createAutoHarnessOrchestrator(config);

        assertThat(orchestrator.getConfig()).isSameAs(config);
        assertThat(orchestrator.getRuntime().getCurrentWorkspace()).isEqualTo("workspace-a");
    }

    @Test
    void factoryAcceptsExplicitAgentAndRails() {
        AutoHarnessConfig config = config();
        DeepAgent agent = new DeepAgent();
        RailWithDeepAgent rail = new RailWithDeepAgent(new DeepAgent());

        AutoHarnessOrchestrator orchestrator = AutoHarnessOrchestrator.createAutoHarnessOrchestrator(
                config,
                agent,
                List.of(rail)
        );

        assertThat(orchestrator.getAgent()).isSameAs(agent);
        assertThat(orchestrator.getStreamRails()).contains(rail);
    }

    @Test
    void inferredAgentUsesFirstRailDeepAgent() {
        DeepAgent deepAgent = new DeepAgent();

        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config(), null, List.of(new RailWithDeepAgent(deepAgent)));

        assertThat(orchestrator.getAgent()).isSameAs(deepAgent);
    }

    @Test
    void getStreamRailsReturnsDefensiveCopy() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());

        List<AgentRail> rails = orchestrator.getStreamRails();
        rails.clear();

        assertThat(orchestrator.getStreamRails()).isNotEmpty();
    }

    @Test
    void cancellationStateResetsForNewSession() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());
        orchestrator.cancel();
        assertThat(orchestrator.shouldCancel()).isTrue();

        Iterator<Object> chunks = orchestrator.runSessionStream(List.of());

        assertThat(orchestrator.shouldCancel()).isFalse();
        assertThat(chunks).toIterable().isNotEmpty();
    }

    @Test
    void messageDispatchResolvesPendingInteractionAndReturnsEmptyIterator() throws Exception {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());
        CompletableFuture<Object> future = orchestrator.createInteraction("confirm-1");

        Iterator<Object> chunks = orchestrator.runSessionStream(null, Map.of(
                "interaction_id", "confirm-1",
                "answer", "yes"
        ));

        assertThat(chunks.hasNext()).isFalse();
        assertThat(future.get()).isEqualTo(Map.of("interaction_id", "confirm-1", "answer", "yes"));
        assertThat(orchestrator.resolveInteraction("confirm-1", Map.of())).isFalse();
    }

    @Test
    void dispatchMessageIgnoresMissingInteractionId() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());

        assertThat(orchestrator.dispatchMessage(Map.of("answer", "yes"))).isFalse();
        assertThat(orchestrator.dispatchMessage(null)).isFalse();
    }

    @Test
    void dispatchMessageCompletesPendingInteraction() throws Exception {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());
        CompletableFuture<Object> future = orchestrator.createInteraction("confirm-2");
        Map<String, Object> message = Map.of("interaction_id", "confirm-2", "answer", "ok");

        assertThat(orchestrator.dispatchMessage(message)).isTrue();

        assertThat(future.get()).isEqualTo(message);
        assertThat(orchestrator.resolveInteraction("confirm-2", message)).isFalse();
    }

    @Test
    void runSessionStreamSelectsPipelineStoresArtifactsAndFinishes() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());
        OptimizationTask task = OptimizationTask.builder()
                .topic("吸收 hermes 能力")
                .build();

        List<Object> chunks = toList(orchestrator.runSessionStream(List.of(task)));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(5);
        assertThat(((OutputSchema) chunks.get(0)).getPayload()).isEqualTo(Map.of("content", "会话启动"));
        OutputSchema pipelineMessage = (OutputSchema) chunks.get(1);
        assertThat(pipelineMessage.getPayload()).isEqualTo(Map.of(
                "content", "Session pipeline: " + AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE,
                "pipeline", AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE,
                "stages", List.of()
        ));
        assertThat(stagePayloads(chunks)).contains("build_verify:running", "build_verify:success");
        OutputSchema finished = (OutputSchema) chunks.get(chunks.size() - 1);
        assertThat(finished.getType()).isEqualTo("harness_session_finished");
        assertThat(orchestrator.getRuntime().getSelectedPipeline())
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(orchestrator.getArtifacts().get("input_tasks")).isEqualTo(List.of(task));
        assertThat(orchestrator.getArtifacts().get("pipeline_selection")).isNotNull();
    }

    @Test
    void runSessionStreamWithNullTasksStillStartsAndFinishes() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());

        List<Object> chunks = toList(orchestrator.runSessionStream(null));

        assertThat(chunks).isNotEmpty();
        assertThat(((OutputSchema) chunks.get(0)).getPayload()).isEqualTo(Map.of("content", "会话启动"));
        assertThat(((OutputSchema) chunks.get(chunks.size() - 1)).getType()).isEqualTo("harness_session_finished");
        assertThat(orchestrator.getArtifacts().get("input_tasks")).isNull();
    }

    @Test
    void defaultPipelineSelectionUsesMetaForEmptyTasks() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());

        assertThat(orchestrator.selectSessionPipeline(List.of()).getPipelineName())
                .isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
    }

    @Test
    void extendedPipelinePreferenceOverridesDefaultSelection() {
        AutoHarnessConfig config = config();
        config.setPipelinePreference(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config);

        assertThat(orchestrator.selectSessionPipeline(null).getPipelineName())
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
    }

    @Test
    void runPipelineStreamReturnsEmptyForObjectPlaceholderPipeline() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());
        orchestrator.getPipelineRegistry().register(
                com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec.builder()
                        .name("placeholder")
                        .pipelineCls(Object.class)
                        .build()
        );

        assertThat(toList(orchestrator.runPipelineStream("placeholder"))).isEmpty();
    }

    @Test
    void resultAndRuntimeHelpersMirrorPythonProperties() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());
        CycleResult result = CycleResult.builder().success(true).summary("done").build();

        orchestrator.recordCycleResult(result);
        OutputSchema message = orchestrator.messageOutput("hello");
        Path sessionDir = orchestrator.ensureSessionRuntimeDir();

        assertThat(orchestrator.getLastCycleResult()).isSameAs(result);
        assertThat(orchestrator.getResults()).containsExactly(result);
        assertThat(message.getPayload()).isEqualTo(Map.of("content", "hello"));
        assertThat(sessionDir).isDirectory();
        assertThat(sessionDir.getFileName().toString()).isEqualTo(orchestrator.getRuntime().getSessionId());
    }

    @Test
    void staticHelpersMirrorPythonUtilityFunctions() {
        String path = AutoHarnessOrchestrator.writeDebugArtifact(
                tempDir.toString(),
                "phase/debug.txt",
                "content"
        );

        assertThat(Path.of(path)).hasContent("content");
        assertThat(AutoHarnessOrchestrator.emptyIterator().hasNext()).isFalse();
        DeepAgent agent = new DeepAgent();
        assertThat(AutoHarnessOrchestrator.inferAgentFromRails(List.of(new RailWithDeepAgent(agent))))
                .isSameAs(agent);
    }

    @Test
    void emptyIteratorHasNoElements() {
        assertThat(AutoHarnessOrchestrator.emptyIterator().hasNext()).isFalse();
    }

    @Test
    void writeDebugArtifactCreatesNestedParentDirectories() {
        String path = AutoHarnessOrchestrator.writeDebugArtifact(
                tempDir.resolve("runs").toString(),
                "nested/phase/debug.txt",
                "debug"
        );

        assertThat(Path.of(path)).hasContent("debug");
        assertThat(Path.of(path).getParent()).isDirectory();
    }

    @Test
    void messageOutputWrapsContentPayload() {
        AutoHarnessOrchestrator orchestrator = new AutoHarnessOrchestrator(config());

        OutputSchema message = orchestrator.messageOutput("hello");

        assertThat(message.getType()).isEqualTo("message");
        assertThat(message.getPayload()).isEqualTo(Map.of("content", "hello"));
    }

    private AutoHarnessConfig config() {
        AutoHarnessConfig config = new AutoHarnessConfig();
        config.setDataDir(tempDir.resolve("data").toString());
        config.setWorkspace("workspace-a");
        config.setConfigBootstrapped(true);
        config.setSuggestedLocalRepo("repo-a");
        config.setPipelinePreference("auto");
        config.setOptimizationGoal("");
        config.setCommunitySkillRepos(List.of());
        config.setMaxTasksPerSession(0);
        return config;
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        java.util.ArrayList<Object> result = new java.util.ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stagePayloads(List<Object> chunks) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (Object chunk : chunks) {
            if (!(chunk instanceof OutputSchema output) || !"stage_result".equals(output.getType())) {
                continue;
            }
            Map<String, Object> payload = (Map<String, Object>) output.getPayload();
            result.add(String.valueOf(payload.get("stage")) + ":" + payload.get("status"));
        }
        return result;
    }

    private static final class RailWithDeepAgent extends AgentRail {
        @SuppressWarnings("unused")
        private final DeepAgent deep_agent;

        private RailWithDeepAgent(DeepAgent deepAgent) {
            this.deep_agent = deepAgent;
        }
    }
}
