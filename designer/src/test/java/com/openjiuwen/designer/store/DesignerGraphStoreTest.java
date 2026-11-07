/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.store;

import com.openjiuwen.designer.schema.DesignerEdge;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerGraphValidationException;
import com.openjiuwen.designer.schema.DesignerLayout;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.schema.DesignerNodeState;
import com.openjiuwen.designer.schema.DesignerSchema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the file-backed designer graph store.
 *
 * @since 2026-11-06
 */
class DesignerGraphStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsNormalizedGraph() throws Exception {
        DesignerGraphStore store = new DesignerGraphStore(tempDir.resolve("designer"));

        store.saveGraph(graph());

        assertThat(Files.readString(store.graphPath("graph_1"))).contains("designer-execution-graph.v1");
        assertThat(store.loadGraph("graph_1")).isPresent()
                .get()
                .extracting(DesignerGraph::title)
                .isEqualTo("Untitled");
    }

    @Test
    void savesAndLoadsRun() {
        DesignerGraphStore store = new DesignerGraphStore(tempDir.resolve("designer"));
        DesignerNodeState state = new DesignerNodeState("completed", 1L, 2L, null, List.of(),
                null, List.of(), null, List.of(), List.of(), "brief text", List.of(), 2);
        DesignerExecutionRun run = new DesignerExecutionRun(DesignerSchema.RUN_SCHEMA_VERSION,
                "run_1", "graph_1", "default", "completed", Map.of("n_brief", state),
                List.of(), 1L, 2L, Map.of("trigger_source", "leader"), null, null, List.of());

        store.saveRun(run);

        assertThat(store.loadRun("run_1")).isPresent()
                .get()
                .satisfies(loaded -> {
                    assertThat(loaded.status()).isEqualTo("completed");
                    assertThat(loaded.nodeStates().get("n_brief").text()).isEqualTo("brief text");
                    assertThat(loaded.nodeStates().get("n_brief").version()).isEqualTo(2);
                });
    }

    @Test
    void rejectsUnsafeGraphId() {
        DesignerGraphStore store = new DesignerGraphStore(tempDir.resolve("designer"));

        assertThatThrownBy(() -> store.loadGraph("../escape"))
                .isInstanceOf(DesignerGraphValidationException.class)
                .hasMessageContaining("safe storage component");
    }

    private DesignerGraph graph() {
        List<DesignerNode> nodes = List.of(
                new DesignerNode("n_brief", "text", "Brief",
                        Map.of("pipeline", "brief"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_character_1", "image", "Character",
                        Map.of("role", "character"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_clip_1", "video", "Clip",
                        Map.of("pipeline", "clip"), DesignerLayout.DEFAULT, null),
                new DesignerNode("n_compose", "video", "Compose",
                        Map.of("pipeline", "compose"), DesignerLayout.DEFAULT, null));
        List<DesignerEdge> edges = List.of(
                new DesignerEdge("e1", "n_brief", "n_character_1", "data", null),
                new DesignerEdge("e2", "n_character_1", "n_clip_1", "data", null),
                new DesignerEdge("e3", "n_clip_1", "n_compose", "data", null));
        return new DesignerGraph(DesignerSchema.GRAPH_SCHEMA_VERSION, "graph_1", "default",
                "", null, null, nodes, edges, Map.of(), 0L, 0L);
    }
}
