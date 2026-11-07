/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

import com.openjiuwen.designer.rpc.DesignerCallContext;
import com.openjiuwen.designer.rpc.DesignerMethods;
import com.openjiuwen.designer.rpc.DesignerRpcFacade;
import com.openjiuwen.designer.rpc.DesignerRpcResult;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerSchema;
import com.openjiuwen.designer.spi.DesignerProjectInfo;
import com.openjiuwen.designer.spi.DesignerProjectPort;
import com.openjiuwen.designer.spi.DesignerSessionCreate;
import com.openjiuwen.designer.spi.DesignerSessionInfo;
import com.openjiuwen.designer.spi.DesignerSessionPort;
import com.openjiuwen.designer.store.DesignerGraphStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Workspace and RPC facade tests with in-memory host ports.
 *
 * @since 2026-11-06
 */
class DesignerWorkspaceServiceTest {

    @TempDir
    Path tempDir;

    private final InMemoryProjectPort projects = new InMemoryProjectPort();
    private final InMemorySessionPort sessions = new InMemorySessionPort();

    @Test
    void createsWorkspaceIdempotently() {
        DesignerWorkspaceService service = service();

        DesignerWorkspace first = service.create("token-1", "ocean film", List.of("a.png"), "model");
        DesignerWorkspace second = service.create("token-1", "ocean film", List.of("a.png"), "model");

        assertThat(second.graph().graphId()).isEqualTo(first.graph().graphId());
        assertThat(first.graph().metadata().get("session_id")).isEqualTo(first.session().sessionId());
        assertThat(first.graph().metadata().get("work_mode")).isEqualTo("design");
        assertThat(first.sessions()).hasSize(1);
    }

    @Test
    void createsAdditionalSessionAndComposesBlankCanvas() {
        DesignerWorkspaceService service = service();
        DesignerWorkspace first = service.create("token-1", "ocean film", List.of(), "model");

        DesignerWorkspace second = service.createSession(first.project().projectId(), "second");
        DesignerWorkspace composed = service.compose(second.project().projectId(),
                second.session().sessionId(), "new prompt", List.of("ref.png"), "model-2");

        assertThat(second.session().title()).isEqualTo("second");
        assertThat(composed.graph().description()).isEqualTo("new prompt");
        assertThat(composed.graph().metadata().get("model_name")).isEqualTo("model-2");
    }

    @Test
    void patchesGraphAndRejectsComposeAfterNodesExist() {
        DesignerWorkspaceService service = service();
        DesignerWorkspace workspace = service.create("token-1", "ocean film", List.of(), "model");

        DesignerGraph patched = service.patchGraph(workspace.graph().graphId(), Map.of(
                "upsert_nodes", List.of(Map.of(
                        "id", "n_brief", "type", "text", "label", "Brief",
                        "config", Map.of("pipeline", "brief")))));

        assertThat(patched.nodes()).hasSize(1);
        assertThatThrownBy(() -> service.compose(workspace.project().projectId(),
                workspace.session().sessionId(), "again", List.of(), "model"))
                .isInstanceOf(DesignerConflictException.class)
                .hasMessageContaining("canvas");
    }

    @Test
    void facadeReturnsWorkspaceGraphAndCapabilities() {
        DesignerRpcFacade facade = new DesignerRpcFacade(service());

        DesignerRpcResult created = facade.handle(DesignerMethods.WORKSPACE_CREATE,
                Map.of("create_token", "token-1", "prompt", "ocean film"),
                DesignerCallContext.EMPTY);

        assertThat(created.ok()).isTrue();
        DesignerGraph graph = (DesignerGraph) created.payload().get("graph");
        assertThat(facade.handle(DesignerMethods.GRAPH_GET,
                Map.of("graph_id", graph.graphId()), DesignerCallContext.EMPTY).ok()).isTrue();
        assertThat(facade.handle(DesignerMethods.MEDIA_CAPABILITIES,
                Map.of("graph_id", graph.graphId()), DesignerCallContext.EMPTY).ok()).isTrue();
        assertThat(facade.handle(DesignerMethods.RUN_PAUSE,
                Map.of("run_id", "run_1"), DesignerCallContext.EMPTY).code())
                .isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void facadeRejectsUnknownGraph() {
        DesignerRpcFacade facade = new DesignerRpcFacade(service());

        DesignerRpcResult result = facade.handle(DesignerMethods.GRAPH_GET,
                Map.of("graph_id", "graph_missing"), DesignerCallContext.EMPTY);

        assertThat(result.ok()).isFalse();
        assertThat(result.code()).isEqualTo("NOT_FOUND");
    }

    private DesignerWorkspaceService service() {
        DesignerGraphStore store = new DesignerGraphStore(tempDir.resolve("designer"));
        return new DesignerWorkspaceService(store, sessions, projects,
                new DesignerDefaultWorkspacePort(tempDir.resolve("designer")));
    }

    private static final class InMemoryProjectPort implements DesignerProjectPort {
        private final DesignerProjectInfo defaultProject =
                new DesignerProjectInfo("default", "默认项目", "", "design");

        @Override
        public DesignerProjectInfo ensureDefaultDesignProject() {
            return defaultProject;
        }

        @Override
        public Optional<DesignerProjectInfo> findProject(String projectId) {
            return "default".equals(projectId) ? Optional.of(defaultProject) : Optional.empty();
        }

        @Override
        public List<DesignerProjectInfo> listProjects() {
            return List.of(defaultProject);
        }
    }

    private static final class InMemorySessionPort implements DesignerSessionPort {
        private final AtomicInteger sequence = new AtomicInteger();
        private final Map<String, DesignerSessionInfo> values = new LinkedHashMap<>();

        @Override
        public DesignerSessionInfo createSession(DesignerSessionCreate request) {
            String sessionId = "design_" + sequence.incrementAndGet();
            DesignerSessionInfo session = new DesignerSessionInfo(sessionId, request.title(),
                    request.projectId(), request.projectDir(), "design", request.modelName(), null);
            values.put(sessionId, session);
            return session;
        }

        @Override
        public Optional<DesignerSessionInfo> findSession(String sessionId) {
            return Optional.ofNullable(values.get(sessionId));
        }

        @Override
        public List<DesignerSessionInfo> listSessions(String projectId) {
            List<DesignerSessionInfo> result = new ArrayList<>();
            for (DesignerSessionInfo session : values.values()) {
                if (projectId.equals(session.projectId())) {
                    result.add(session);
                }
            }
            return result;
        }

        @Override
        public void updateSession(String sessionId, Map<String, Object> patch) {
            DesignerSessionInfo current = values.get(sessionId);
            if (current == null) {
                return;
            }
            values.put(sessionId, new DesignerSessionInfo(current.sessionId(), current.title(),
                    current.projectId(), current.projectDir(), current.workMode(),
                    String.valueOf(patch.getOrDefault("model", current.modelName())),
                    current.graphId()));
        }

        @Override
        public void deleteSession(String sessionId) {
            values.remove(sessionId);
        }
    }
}
