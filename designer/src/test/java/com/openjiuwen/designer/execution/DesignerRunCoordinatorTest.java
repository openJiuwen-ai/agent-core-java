/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.event.DesignerEventSink;
import com.openjiuwen.designer.rpc.DesignerCallContext;
import com.openjiuwen.designer.rpc.DesignerMethods;
import com.openjiuwen.designer.rpc.DesignerRpcFacade;
import com.openjiuwen.designer.rpc.DesignerRpcResult;
import com.openjiuwen.designer.schema.DesignerEdge;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerLayout;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.schema.DesignerSchema;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;
import com.openjiuwen.designer.store.DesignerGraphStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Coordinator and run RPC tests.
 *
 * @since 2026-11-06
 */
class DesignerRunCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void startsRunAndReportsTerminalStateThroughFacade() {
        DesignerGraphStore store = new DesignerGraphStore(tempDir.resolve("designer"));
        DesignerGraph graph = store.saveGraph(graph());
        DesignerRunCoordinator coordinator = new DesignerRunCoordinator(store, handlers(),
                new DesignerEventSink() {
                }, workspace());
        DesignerRpcFacade facade = new DesignerRpcFacade(null, coordinator);

        DesignerRpcResult started = facade.handle(DesignerMethods.RUN_START,
                Map.of("graph_id", graph.graphId()), DesignerCallContext.EMPTY);

        assertThat(started.ok()).isTrue();
        String runId = String.valueOf(((com.openjiuwen.designer.schema.DesignerExecutionRun)
                started.payload().get("run")).runId());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            DesignerRpcResult result = facade.handle(DesignerMethods.RUN_GET,
                    Map.of("run_id", runId), DesignerCallContext.EMPTY);
            assertThat(result.ok()).isTrue();
            assertThat(((com.openjiuwen.designer.schema.DesignerExecutionRun)
                    result.payload().get("run")).status()).isEqualTo("completed");
        });
    }

    private NodeHandlerRegistry handlers() {
        return new NodeHandlerRegistry()
                .register("brief", (node, context) -> NodeOutcome.completedText("brief"))
                .register("character", (node, context) -> NodeOutcome.completedText("character"))
                .register("clip", (node, context) -> NodeOutcome.completedText("clip"))
                .register("compose", (node, context) -> NodeOutcome.completedText("compose"));
    }

    private DesignerWorkspacePort workspace() {
        return new DesignerWorkspacePort() {
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
        };
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
}
