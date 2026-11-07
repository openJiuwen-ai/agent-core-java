/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.spi.DesignerProjectInfo;
import com.openjiuwen.designer.spi.DesignerProjectPort;
import com.openjiuwen.designer.spi.DesignerSessionCreate;
import com.openjiuwen.designer.spi.DesignerSessionInfo;
import com.openjiuwen.designer.spi.DesignerSessionPort;
import com.openjiuwen.designer.spi.DesignerWorkspacePort;
import com.openjiuwen.designer.store.DesignerGraphStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Designer workspace and graph use cases.
 *
 * @since 2026-11-06
 */
public final class DesignerWorkspaceService {

    private static final String DEFAULT_PROJECT_ID = "default";
    private static final String DEFAULT_PROJECT_NAME = "默认项目";
    private static final String DESIGN_TITLE = "设计项目";

    private final DesignerGraphStore store;
    private final DesignerSessionPort sessions;
    private final DesignerProjectPort projects;
    private final DesignerWorkspacePort workspace;
    private final DesignerGraphFactory graphFactory = new DesignerGraphFactory();
    private final DesignerGraphPatch graphPatch = new DesignerGraphPatch();
    private final Map<String, String> graphByToken = new ConcurrentHashMap<>();

    /**
     * Create a workspace service.
     *
     * @param store graph store
     * @param sessions session port
     * @param projects project port
     * @param workspace workspace port
     */
    public DesignerWorkspaceService(DesignerGraphStore store, DesignerSessionPort sessions,
            DesignerProjectPort projects, DesignerWorkspacePort workspace) {
        this.store = store;
        this.sessions = sessions;
        this.projects = projects;
        this.workspace = workspace;
    }

    /**
     * Create a canvas for a prompt.
     *
     * @param createToken idempotency token
     * @param prompt user prompt
     * @param references reference file paths
     * @param modelName selected model
     * @return workspace
     */
    public DesignerWorkspace create(String createToken, String prompt, List<String> references,
            String modelName) {
        String token = requireNonBlank(createToken, "create_token");
        String userPrompt = requireNonBlank(prompt, "prompt");
        String existingGraphId = graphByToken.get(token);
        if (existingGraphId != null) {
            return get(DEFAULT_PROJECT_ID, null, existingGraphId);
        }
        DesignerProjectInfo project = projects.ensureDefaultDesignProject();
        DesignerSessionInfo session = sessions.createSession(new DesignerSessionCreate(
                project.projectId(), "画布 1", userPrompt, modelName, project.projectDir()));
        Map<String, Object> metadata = baseMetadata(project, session, modelName);
        metadata.put("prompt", userPrompt);
        metadata.put("references", references == null ? List.of() : List.copyOf(references));
        DesignerGraph graph = store.saveGraph(graphFactory.empty(
                project.projectId(), DESIGN_TITLE, userPrompt, metadata));
        graphByToken.put(token, graph.graphId());
        return workspace(project, session, graph);
    }

    /**
     * Load a workspace.
     *
     * @param projectId project id; {@code default} selects the virtual project
     * @param sessionId optional session id
     * @return workspace
     */
    public DesignerWorkspace get(String projectId, String sessionId) {
        return get(projectId, sessionId, null);
    }

    /**
     * Create a new session and empty canvas.
     *
     * @param projectId project id
     * @param title optional title
     * @return workspace
     */
    public DesignerWorkspace createSession(String projectId, String title) {
        DesignerProjectInfo project = resolveProject(projectId);
        List<DesignerSessionInfo> existing = sessions.listSessions(project.projectId());
        String resolvedTitle = title == null || title.isBlank()
                ? "画布 " + (existing.size() + 1)
                : title.strip();
        DesignerSessionInfo session = sessions.createSession(new DesignerSessionCreate(
                project.projectId(), resolvedTitle, "", "", project.projectDir()));
        Map<String, Object> metadata = baseMetadata(project, session, "");
        DesignerGraph graph = store.saveGraph(graphFactory.empty(
                project.projectId(), DESIGN_TITLE, "", metadata));
        return workspace(project, session, graph);
    }

    /**
     * Compose a blank canvas with a prompt and references.
     *
     * @param projectId project id
     * @param sessionId session id
     * @param prompt user prompt
     * @param references reference file paths
     * @param modelName selected model
     * @return workspace
     */
    public DesignerWorkspace compose(String projectId, String sessionId, String prompt,
            List<String> references, String modelName) {
        DesignerProjectInfo project = resolveProject(projectId);
        DesignerSessionInfo session = sessions.findSession(requireNonBlank(sessionId, "session_id"))
                .orElseThrow(() -> new DesignerNotFoundException("session not found: " + sessionId));
        DesignerGraph graph = findGraphForSession(session.sessionId())
                .orElseThrow(() -> new DesignerNotFoundException("graph not found for session: "
                        + session.sessionId()));
        if (!isBlankCanvas(graph)) {
            throw new DesignerConflictException("canvas already contains generated nodes");
        }
        Map<String, Object> metadata = new LinkedHashMap<>(graph.metadata());
        metadata.put("model_name", modelName == null ? "" : modelName);
        metadata.put("prompt", prompt == null ? "" : prompt);
        metadata.put("references", references == null ? List.of() : List.copyOf(references));
        DesignerGraph updated = store.saveGraph(graph.withDescription(prompt == null ? "" : prompt)
                .withMetadata(metadata).withUpdatedAt(System.currentTimeMillis()));
        Map<String, Object> sessionPatch = new LinkedHashMap<>();
        sessionPatch.put("title", session.title());
        sessionPatch.put("model", modelName == null ? "" : modelName);
        sessions.updateSession(session.sessionId(), sessionPatch);
        return workspace(project, session, updated);
    }

    /**
     * Load one graph.
     *
     * @param graphId graph id
     * @return graph
     */
    public DesignerGraph getGraph(String graphId) {
        return store.loadGraph(graphId)
                .orElseThrow(() -> new DesignerNotFoundException("graph not found: " + graphId));
    }

    /**
     * List graphs, optionally scoped to one project.
     *
     * @param projectId project id; blank or {@code default} lists all
     * @return graphs
     */
    public List<DesignerGraph> listGraphs(String projectId) {
        List<DesignerGraph> graphs = store.listGraphs();
        if (projectId == null || projectId.isBlank() || DEFAULT_PROJECT_ID.equals(projectId)) {
            return graphs;
        }
        List<DesignerGraph> scoped = new ArrayList<>();
        for (DesignerGraph graph : graphs) {
            if (projectId.equals(graph.projectId())) {
                scoped.add(graph);
            }
        }
        return scoped;
    }

    /**
     * Save a graph.
     *
     * @param graph graph to save
     * @return saved graph
     */
    public DesignerGraph saveGraph(DesignerGraph graph) {
        return store.saveGraph(graph);
    }

    /**
     * Patch a graph.
     *
     * @param graphId graph id
     * @param patch patch object
     * @return patched graph
     */
    public DesignerGraph patchGraph(String graphId, Map<String, Object> patch) {
        DesignerGraph current = getGraph(graphId);
        return store.saveGraph(graphPatch.apply(current, patch));
    }

    /**
     * Find the graph paired with a session.
     *
     * @param sessionId session id
     * @return graph when present
     */
    public Optional<DesignerGraph> findGraphForSession(String sessionId) {
        DesignerGraph best = null;
        for (DesignerGraph graph : store.listGraphs()) {
            Object owner = graph.metadata().get("session_id");
            if (owner != null && sessionId.equals(String.valueOf(owner))
                    && (best == null || graph.updatedAt() > best.updatedAt())) {
                best = graph;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Return the workspace filesystem port.
     *
     * @return workspace port
     */
    public DesignerWorkspacePort workspacePort() {
        return workspace;
    }

    private DesignerWorkspace get(String projectId, String sessionId, String graphId) {
        DesignerProjectInfo project = resolveProject(projectId);
        List<DesignerSessionInfo> existing = sessions.listSessions(project.projectId());
        if (existing.isEmpty() && (sessionId == null || sessionId.isBlank())) {
            return createSession(project.projectId(), "");
        }
        DesignerSessionInfo session = selectSession(existing, sessionId);
        DesignerGraph graph = graphId == null
                ? findGraphForSession(session.sessionId()).orElseThrow(() ->
                        new DesignerNotFoundException("graph not found for session: " + session.sessionId()))
                : getGraph(graphId);
        return workspace(project, session, graph);
    }

    private DesignerSessionInfo selectSession(List<DesignerSessionInfo> sessions, String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessions.stream().filter(item -> sessionId.equals(item.sessionId())).findFirst()
                    .orElseThrow(() -> new DesignerNotFoundException("session not found: " + sessionId));
        }
        if (sessions.isEmpty()) {
            throw new DesignerNotFoundException("project has no design sessions");
        }
        return sessions.get(sessions.size() - 1);
    }

    private DesignerProjectInfo resolveProject(String projectId) {
        if (projectId == null || projectId.isBlank() || DEFAULT_PROJECT_ID.equals(projectId)) {
            return projects.ensureDefaultDesignProject();
        }
        return projects.findProject(projectId)
                .orElseThrow(() -> new DesignerNotFoundException("project not found: " + projectId));
    }

    private DesignerWorkspace workspace(DesignerProjectInfo project, DesignerSessionInfo session,
            DesignerGraph graph) {
        return new DesignerWorkspace(project, session, graph,
                sessions.listSessions(project.projectId()));
    }

    private static Map<String, Object> baseMetadata(DesignerProjectInfo project,
            DesignerSessionInfo session, String modelName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("session_id", session.sessionId());
        metadata.put("project_dir", project.projectDir());
        metadata.put("project_name", project.name());
        metadata.put("work_mode", "design");
        metadata.put("model_name", modelName == null ? "" : modelName);
        return metadata;
    }

    private static boolean isBlankCanvas(DesignerGraph graph) {
        for (DesignerNode node : graph.nodes()) {
            if (!isUserReference(node)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUserReference(DesignerNode node) {
        Object userReference = node.config().get("user_reference");
        if (Boolean.TRUE.equals(userReference)) {
            return true;
        }
        return "user_reference".equals(node.config().get("role"))
                || "user_reference".equals(node.config().get("pipeline"));
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
