/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.event.DesignerEventSink;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerEdge;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerLayout;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.schema.DesignerSchema;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;
import com.openjiuwen.designer.store.DesignerGraphStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Execution tests for full, frozen, single-node, and cascade runs.
 *
 * @since 2026-11-06
 */
class DesignerGraphRunnerTest {

    @TempDir
    Path tempDir;

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void executesFullGraphAndFrozenRerunDoesNotCallHandlers() {
        DesignerGraphRunner runner = runner();
        DesignerGraph graph = graph();

        DesignerExecutionRun first = runner.run(graph, run(), null, false);
        assertThat(first.status()).isEqualTo("completed");
        assertThat(first.nodeStates().get("n_clip_1").status()).isEqualTo("completed");
        assertThat(calls.get()).isEqualTo(4);

        DesignerExecutionRun second = runner.run(graph, runFrom(first), null, false);
        assertThat(second.status()).isEqualTo("completed");
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void singleNodeRunForcesTargetAndFreezesAncestors() {
        DesignerGraphRunner runner = runner();
        DesignerGraph graph = graph();
        DesignerExecutionRun first = runner.run(graph, run(), null, false);

        DesignerExecutionRun second = runner.run(graph, runFrom(first), "n_clip_1", false);

        assertThat(second.status()).isEqualTo("completed");
        assertThat(calls.get()).isEqualTo(5);
        assertThat(second.nodeStates().get("n_clip_1").status()).isEqualTo("completed");
    }

    @Test
    void cascadeRunForcesTargetAndDownstream() {
        DesignerGraphRunner runner = runner();
        DesignerGraph graph = graph();
        DesignerExecutionRun first = runner.run(graph, run(), null, false);

        DesignerExecutionRun second = runner.run(graph, runFrom(first), "n_character_1", true);

        assertThat(second.status()).isEqualTo("completed");
        assertThat(calls.get()).isEqualTo(7);
    }

    private DesignerGraphRunner runner() {
        DesignerGraphStore store = new DesignerGraphStore(tempDir.resolve("designer"));
        NodeHandlerRegistry handlers = new NodeHandlerRegistry()
                .register("brief", (node, context) -> text("brief"))
                .register("character", (node, context) -> media("image", "character.png"))
                .register("clip", (node, context) -> media("video", "clip.mp4"))
                .register("compose", (node, context) -> media("video", "final.mp4"));
        return new DesignerGraphRunner(store, handlers, new DesignerEventSink() {
        }, new DesignerWorkspacePort() {
            @Override
            public Path graphRoot() {
                return tempDir.resolve("designer");
            }

            @Override
            public Path outputDir(DesignerGraph graph) {
                return tempDir.resolve("outputs");
            }

            @Override
            public Path mediaDir(DesignerGraph graph) {
                return tempDir.resolve("media");
            }
        });
    }

    private NodeOutcome text(String value) {
        calls.incrementAndGet();
        return NodeOutcome.completedText(value);
    }

    private NodeOutcome media(String kind, String name) {
        calls.incrementAndGet();
        DesignerAssetRef ref = new DesignerAssetRef(kind, "file:///tmp/" + name,
                kind.equals("video") ? "video/mp4" : "image/png", name);
        return NodeOutcome.completedMedia(name, ref, List.of(ref));
    }

    private DesignerGraph graph() {
        List<DesignerNode> nodes = List.of(
                new DesignerNode("n_brief", "text", "Brief",
                        Map.of("pipeline", "brief"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_character_1", "image", "Character",
                        Map.of("pipeline", "character"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_clip_1", "video", "Clip",
                        Map.of("pipeline", "clip"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_compose", "video", "Compose",
                        Map.of("pipeline", "compose"), DesignerLayout.DEFAULT, null));
        List<DesignerEdge> edges = List.of(
                new DesignerEdge("e1", "n_brief", "n_character_1", "data", null),
                new DesignerEdge("e2", "n_character_1", "n_clip_1", "data", null),
                new DesignerEdge("e3", "n_clip_1", "n_compose", "data", null));
        return new DesignerGraph(DesignerSchema.GRAPH_SCHEMA_VERSION, "graph_1", "default",
                "title", "", "manual", nodes, edges, Map.of("session_id", "design_1"), 1L, 1L);
    }

    private DesignerExecutionRun run() {
        long now = System.currentTimeMillis();
        return new DesignerExecutionRun(DesignerSchema.RUN_SCHEMA_VERSION, "run_1", "graph_1",
                "default", "running", Map.of(), List.of(), now, now,
                Map.of("trigger_source", "leader"), null, null, List.of());
    }

    private DesignerExecutionRun runFrom(DesignerExecutionRun previous) {
        return new DesignerExecutionRun(previous.schemaVersion(), previous.runId(),
                previous.graphId(), previous.projectId(), "running", previous.nodeStates(),
                List.of(), previous.createdAt(), System.currentTimeMillis(), previous.metadata(),
                null, null, List.of());
    }
}
