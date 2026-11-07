/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.rpc;

import com.openjiuwen.designer.DesignerJson;
import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.execution.DesignerRunCoordinator;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerGraphValidationException;
import com.openjiuwen.designer.spi.DesignerProjectInfo;
import com.openjiuwen.designer.spi.DesignerSessionInfo;
import com.openjiuwen.designer.store.DesignerStoreException;
import com.openjiuwen.designer.workspace.DesignerConflictException;
import com.openjiuwen.designer.workspace.DesignerNotFoundException;
import com.openjiuwen.designer.workspace.DesignerWorkspace;
import com.openjiuwen.designer.workspace.DesignerWorkspaceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport-neutral implementation of the designer RPC contract.
 *
 * <p>The host maps its wire method name and envelope to this facade; the facade
 * never depends on WebSocket, E2A, or Spring types.</p>
 *
 * @since 2026-11-06
 */
public final class DesignerRpcFacade {

    private static final String DEFAULT_PROJECT_ID = "default";
    private static final String DEFAULT_MODEL = "MiniMax-H3";
    private static final String DEFAULT_IMAGE_MODEL = "image-01";

    private final DesignerWorkspaceService workspaceService;
    private final DesignerRunCoordinator runCoordinator;

    /**
     * Create the facade without run execution.
     *
     * @param workspaceService workspace and graph use cases
     */
    public DesignerRpcFacade(DesignerWorkspaceService workspaceService) {
        this(workspaceService, null);
    }

    /**
     * Create the facade.
     *
     * @param workspaceService workspace and graph use cases
     * @param runCoordinator run coordinator, or {@code null} when run execution is unavailable
     */
    public DesignerRpcFacade(DesignerWorkspaceService workspaceService,
            DesignerRunCoordinator runCoordinator) {
        this.workspaceService = workspaceService;
        this.runCoordinator = runCoordinator;
    }

    /**
     * Dispatch one designer RPC.
     *
     * @param method wire method name
     * @param params request parameters
     * @param context caller context
     * @return result
     */
    public DesignerRpcResult handle(String method, Map<String, Object> params,
            DesignerCallContext context) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        try {
            return switch (method) {
                case DesignerMethods.WORKSPACE_CREATE -> workspaceCreate(safeParams);
                case DesignerMethods.WORKSPACE_GET -> workspaceGet(safeParams);
                case DesignerMethods.WORKSPACE_SESSION_CREATE -> sessionCreate(safeParams);
                case DesignerMethods.WORKSPACE_SESSION_COMPOSE -> sessionCompose(safeParams);
                case DesignerMethods.GRAPH_GET -> graphGet(safeParams);
                case DesignerMethods.GRAPH_LIST -> graphList(safeParams);
                case DesignerMethods.GRAPH_SAVE -> graphSave(safeParams);
                case DesignerMethods.GRAPH_PATCH -> graphPatch(safeParams);
                case DesignerMethods.MEDIA_CAPABILITIES -> mediaCapabilities(safeParams);
                case DesignerMethods.RUN_START -> runStart(safeParams);
                case DesignerMethods.RUN_GET -> runGet(safeParams);
                case DesignerMethods.RUN_CANCEL -> runCancel(safeParams);
                case DesignerMethods.RUN_PAUSE -> throw new DesignerRpcException(
                        DesignerErrorCode.NOT_IMPLEMENTED,
                        "team.design runs do not support pause; cancel the trigger and re-run");
                case DesignerMethods.AGENT_TEMPLATES_LIST -> ok(Map.of("templates", templates()));
                case DesignerMethods.AGENT_GROUPS_SHOW -> ok(Map.of("group", designerGroup()));
                default -> throw new DesignerRpcException(DesignerErrorCode.BAD_REQUEST,
                        "unknown designer method: " + method);
            };
        } catch (DesignerRpcException ex) {
            return DesignerRpcResult.error(ex.code(), ex.getMessage());
        } catch (DesignerNotFoundException ex) {
            return DesignerRpcResult.error(DesignerErrorCode.NOT_FOUND, ex.getMessage());
        } catch (DesignerConflictException ex) {
            return DesignerRpcResult.error(DesignerErrorCode.CONFLICT, ex.getMessage());
        } catch (DesignerGraphValidationException | DesignerNodeException
                | IllegalArgumentException ex) {
            return DesignerRpcResult.error(DesignerErrorCode.BAD_REQUEST, ex.getMessage());
        } catch (DesignerStoreException ex) {
            return DesignerRpcResult.error(DesignerErrorCode.INTERNAL_ERROR,
                    "designer storage failure");
        }
    }

    private DesignerRpcResult workspaceCreate(Map<String, Object> params) {
        DesignerWorkspace workspace = workspaceService.create(
                stringParam(params, "create_token", ""),
                stringParam(params, "prompt", ""),
                stringList(params.get("references")),
                stringParam(params, "model_name", ""));
        return ok(workspacePayload(workspace));
    }

    private DesignerRpcResult workspaceGet(Map<String, Object> params) {
        DesignerWorkspace workspace = workspaceService.get(
                stringParam(params, "project_id", DEFAULT_PROJECT_ID),
                stringParam(params, "session_id", ""));
        return ok(workspacePayload(workspace));
    }

    private DesignerRpcResult sessionCreate(Map<String, Object> params) {
        DesignerWorkspace workspace = workspaceService.createSession(
                stringParam(params, "project_id", DEFAULT_PROJECT_ID),
                stringParam(params, "title", ""));
        return ok(workspacePayload(workspace));
    }

    private DesignerRpcResult sessionCompose(Map<String, Object> params) {
        DesignerWorkspace workspace = workspaceService.compose(
                stringParam(params, "project_id", DEFAULT_PROJECT_ID),
                stringParam(params, "session_id", ""),
                stringParam(params, "prompt", ""),
                stringList(params.get("references")),
                stringParam(params, "model_name", ""));
        return ok(workspacePayload(workspace));
    }

    private DesignerRpcResult graphGet(Map<String, Object> params) {
        DesignerGraph graph = workspaceService.getGraph(stringParam(params, "graph_id", ""));
        return ok(Map.of("graph", graph));
    }

    private DesignerRpcResult graphList(Map<String, Object> params) {
        List<DesignerGraph> graphs = workspaceService.listGraphs(
                stringParam(params, "project_id", DEFAULT_PROJECT_ID));
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (DesignerGraph graph : graphs) {
            summaries.add(graphSummary(graph));
        }
        return ok(Map.of("graphs", List.copyOf(graphs), "summaries", summaries));
    }

    private DesignerRpcResult graphSave(Map<String, Object> params) {
        DesignerGraph graph = graphParam(params.get("graph"));
        return ok(Map.of("graph", workspaceService.saveGraph(graph)));
    }

    private DesignerRpcResult graphPatch(Map<String, Object> params) {
        String graphId = stringParam(params, "graph_id", "");
        Map<String, Object> patch = mapParam(params.get("patch"));
        if (patch.isEmpty()) {
            patch = topLevelPatch(params);
        }
        return ok(Map.of("graph", workspaceService.patchGraph(graphId, patch)));
    }

    private DesignerRpcResult mediaCapabilities(Map<String, Object> params) {
        String graphId = stringParam(params, "graph_id", "");
        Map<String, Object> payload = new LinkedHashMap<>(mediaCapabilitiesPayload());
        if (!graphId.isBlank()) {
            workspaceService.getGraph(graphId);
            payload.put("settings", Map.of(
                    "video", Map.of("aspect_ratio", "16:9", "resolution", "768P"),
                    "image", Map.of("aspect_ratio", "16:9", "resolution", "1K")));
            payload.put("video_durations", Map.of());
        }
        return ok(payload);
    }

    private DesignerRpcResult runStart(Map<String, Object> params) {
        DesignerRunCoordinator coordinator = requireRunCoordinator();
        String graphId = stringParam(params, "graph_id", "");
        if (graphId.isBlank()) {
            throw new DesignerRpcException(DesignerErrorCode.BAD_REQUEST, "graph_id is required");
        }
        DesignerRunCoordinator.DesignerRunStartResult started = coordinator.start(
                graphId, stringParam(params, "node_id", ""), booleanParam(params.get("cascade")),
                "canvas");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run", started.run());
        payload.put("warning", started.warnings().isEmpty() ? "" : started.warnings().get(0));
        payload.put("warnings", started.warnings());
        return ok(payload);
    }

    private DesignerRpcResult runGet(Map<String, Object> params) {
        DesignerRunCoordinator coordinator = requireRunCoordinator();
        String runId = stringParam(params, "run_id", "");
        if (!runId.isBlank()) {
            return ok(Map.of("run", coordinator.get(runId)));
        }
        String graphId = stringParam(params, "graph_id", "");
        if (graphId.isBlank()) {
            throw new DesignerRpcException(DesignerErrorCode.BAD_REQUEST,
                    "run_id or graph_id is required");
        }
        DesignerGraph graph = workspaceService.getGraph(graphId);
        DesignerExecutionRun run = coordinator.runForGraph(graph)
                .orElseThrow(() -> new DesignerNotFoundException("run not found for graph: " + graphId));
        return ok(Map.of("run", run));
    }

    private DesignerRpcResult runCancel(Map<String, Object> params) {
        DesignerRunCoordinator coordinator = requireRunCoordinator();
        String runId = stringParam(params, "run_id", "");
        if (runId.isBlank()) {
            throw new DesignerRpcException(DesignerErrorCode.BAD_REQUEST, "run_id is required");
        }
        return ok(Map.of("run", coordinator.cancel(runId)));
    }

    private DesignerRunCoordinator requireRunCoordinator() {
        if (runCoordinator == null) {
            throw new DesignerRpcException(DesignerErrorCode.NOT_IMPLEMENTED,
                    "designer run is not wired yet");
        }
        return runCoordinator;
    }

    private static boolean booleanParam(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> workspacePayload(DesignerWorkspace workspace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project", projectPayload(workspace.project()));
        payload.put("session", sessionPayload(workspace.session()));
        payload.put("graph", workspace.graph());
        payload.put("messages", List.of());
        payload.put("sessions", workspace.sessions().stream()
                .map(DesignerRpcFacade::sessionSummary)
                .toList());
        return payload;
    }

    private static Map<String, Object> projectPayload(DesignerProjectInfo project) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_id", project.projectId());
        payload.put("name", project.name());
        payload.put("project_dir", project.projectDir());
        payload.put("work_mode", project.workMode());
        return payload;
    }

    private static Map<String, Object> sessionPayload(DesignerSessionInfo session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", session.sessionId());
        payload.put("title", session.title());
        payload.put("project_id", session.projectId());
        payload.put("project_dir", session.projectDir());
        payload.put("work_mode", session.workMode());
        payload.put("model", session.modelName());
        if (session.graphId() != null && !session.graphId().isBlank()) {
            payload.put("graph_id", session.graphId());
        }
        return payload;
    }

    private static Map<String, Object> sessionSummary(DesignerSessionInfo session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", session.sessionId());
        payload.put("title", session.title());
        payload.put("project_id", session.projectId());
        if (session.graphId() != null && !session.graphId().isBlank()) {
            payload.put("graph_id", session.graphId());
        }
        return payload;
    }

    private static Map<String, Object> graphSummary(DesignerGraph graph) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("graph_id", graph.graphId());
        payload.put("project_id", graph.projectId());
        payload.put("title", graph.title());
        payload.put("updated_at", graph.updatedAt());
        payload.put("schema_version", graph.schemaVersion());
        payload.put("project_dir", graph.metadata().getOrDefault("project_dir", ""));
        payload.put("project_name", graph.metadata().getOrDefault("project_name", ""));
        payload.put("session_id", graph.metadata().getOrDefault("session_id", ""));
        payload.put("run_id", graph.metadata().getOrDefault("team_design_run_id", ""));
        payload.put("run_status", "");
        payload.put("has_video", hasVideo(graph));
        payload.put("clip_label", "");
        return payload;
    }

    private static boolean hasVideo(DesignerGraph graph) {
        return graph.nodes().stream().anyMatch(node -> node.outputRef() != null
                && "video".equals(node.outputRef().kind()));
    }

    private static Map<String, Object> mediaCapabilitiesPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("video", videoCapabilities());
        payload.put("image", imageCapabilities());
        return payload;
    }

    private static Map<String, Object> videoCapabilities() {
        Map<String, Object> video = new LinkedHashMap<>();
        video.put("configured_model", DEFAULT_MODEL);
        video.put("backend", "minimax");
        video.put("model_id", DEFAULT_MODEL);
        video.put("provider", "minimax");
        video.put("ratios", List.of("21:9", "16:9", "4:3", "1:1", "3:4", "9:16"));
        video.put("resolutions", List.of("768P", "2K"));
        video.put("default_ratio", "16:9");
        video.put("default_resolution", "768P");
        video.put("min_sec", 4);
        video.put("max_sec", 15);
        video.put("default_sec", 5);
        video.put("max_reference_images", 9);
        video.put("supports_reference_images", true);
        return video;
    }

    private static Map<String, Object> imageCapabilities() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("configured_model", DEFAULT_IMAGE_MODEL);
        image.put("backend", "minimax");
        image.put("ratios", List.of("16:9", "4:3", "1:1", "3:4", "9:16"));
        image.put("resolutions", List.of("1K", "1.5K"));
        image.put("default_ratio", "16:9");
        image.put("default_resolution", "1K");
        return image;
    }

    private static List<Map<String, Object>> templates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        templates.add(template("brief", "Brief", "brief"));
        templates.add(template("character", "Character", "character"));
        templates.add(template("scene", "Scene", "scene"));
        templates.add(template("storyboard", "Storyboard", "storyboard"));
        templates.add(template("clip", "Clip", "clip"));
        templates.add(template("compose", "Compose", "compose"));
        return templates;
    }

    private static Map<String, Object> template(String id, String name, String pipeline) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("id", id);
        template.put("name", name);
        template.put("pipeline", pipeline);
        template.put("group", "designer");
        return template;
    }

    private static Map<String, Object> designerGroup() {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", "designer");
        group.put("name", "Designer");
        group.put("members", List.of("brief", "character", "scene", "storyboard", "clip", "compose"));
        return group;
    }

    private static DesignerGraph graphParam(Object raw) {
        if (raw instanceof DesignerGraph graph) {
            return graph;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new DesignerGraphValidationException("graph is required");
        }
        try {
            return DesignerJson.mapper().convertValue(map, DesignerGraph.class);
        } catch (IllegalArgumentException ex) {
            throw new DesignerGraphValidationException("invalid graph payload", ex);
        }
    }

    private static Map<String, Object> mapParam(Object raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static Map<String, Object> topLevelPatch(Map<String, Object> params) {
        Map<String, Object> patch = new LinkedHashMap<>();
        for (String key : List.of("title", "description", "upsert_nodes", "upsert_edges",
                "remove_node_ids", "remove_edge_ids", "video_settings", "image_settings")) {
            if (params.containsKey(key)) {
                patch.put(key, params.get(key));
            }
        }
        return patch;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object value : list) {
            String text = referencePath(value);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private static String referencePath(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("path", "file_path", "local_path", "uri")) {
                Object candidate = map.get(key);
                if (candidate != null && !String.valueOf(candidate).isBlank()) {
                    return String.valueOf(candidate);
                }
            }
            return "";
        }
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? defaultValue
                : String.valueOf(value);
    }

    private static DesignerRpcResult ok(Map<String, Object> payload) {
        return DesignerRpcResult.ok(payload);
    }
}
