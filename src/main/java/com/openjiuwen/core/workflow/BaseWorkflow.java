/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.Router;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.graph.stream_actor.StreamGraph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.ProxySession;
import com.openjiuwen.core.session.internal.RouterSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.IOConfig;
import com.openjiuwen.core.workflow.component.NodeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Base workflow implementation providing graph construction, edge management,
 * component configuration, and ability inference.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow._workflow.BaseWorkflow}.
 */
public class BaseWorkflow {

    private static final Pattern COMP_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final String WORKFLOW_DRAWABLE = "WORKFLOW_DRAWABLE";

    private final Graph graph;
    private final WorkflowConfig workflowConfig;
    private final WorkflowSpec workflowSpec;
    private final StreamGraph streamActor;
    private final ProxySession session;
    // Drawable is optional; kept as Object to avoid hard dependency on visualization module
    private Object drawable;

    public BaseWorkflow() {
        this(null, null);
    }

    public BaseWorkflow(WorkflowConfig workflowConfig, Graph newGraph) {
        this.graph = newGraph != null ? newGraph : new PregelGraph();
        this.workflowConfig = workflowConfig != null ? workflowConfig
                : new WorkflowConfig(WorkflowCard.builder().id(UUID.randomUUID().toString().replace("-", "")).build());
        this.workflowSpec = this.workflowConfig.getSpec();
        this.streamActor = new StreamGraph();
        this.session = new ProxySession();
        this.drawable = null;
    }

    public WorkflowConfig getConfig() {
        return workflowConfig;
    }

    public Graph getGraph() {
        return graph;
    }

    public StreamGraph getStreamActor() {
        return streamActor;
    }

    /**
     * Add a workflow component with full configuration.
     */
    public BaseWorkflow addWorkflowComp(
            String compId,
            ComponentComposable workflowComp,
            Boolean waitForAll,
            Object inputsSchema,
            Object outputsSchema,
            Object streamInputsSchema,
            Object streamOutputsSchema,
            List<ComponentAbility> compAbility) {

        validateCompId(compId);
        validateSchemas(compId, inputsSchema, outputsSchema, streamInputsSchema, streamOutputsSchema);
        validateCompAbility(compId, compAbility, waitForAll);

        NodeConfig nodeSpec = new NodeConfig(
                compAbility != null ? new ArrayList<>(compAbility) : new ArrayList<>(),
                new IOConfig(inputsSchema, outputsSchema),
                new IOConfig(streamInputsSchema, streamOutputsSchema));

        workflowSpec.getCompConfigs().put(compId, nodeSpec);

        boolean wait = waitForAll != null && waitForAll;
        workflowComp.addComponent(graph, compId, wait);

        return this;
    }

    public BaseWorkflow startComp(String startCompId) {
        validateCompId(startCompId);
        graph.startNode(startCompId);
        workflowSpec.getStartNodes().add(startCompId);
        return this;
    }

    public BaseWorkflow endComp(String endCompId) {
        validateCompId(endCompId);
        graph.endNode(endCompId);
        return this;
    }

    public BaseWorkflow addConnection(Object srcCompId, String targetCompId) {
        validateEdge(srcCompId, targetCompId, StatusCode.WORKFLOW_EDGE_INVALID);
        graph.addEdge(srcCompId, targetCompId);

        if (srcCompId instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> srcList = (List<String>) srcCompId;
            for (String sourceId : srcList) {
                workflowSpec.getEdges().computeIfAbsent(sourceId, k -> new ArrayList<>()).add(targetCompId);
            }
        } else {
            workflowSpec.getEdges()
                    .computeIfAbsent((String) srcCompId, k -> new ArrayList<>())
                    .add(targetCompId);
        }
        return this;
    }

    public BaseWorkflow addStreamConnection(String srcCompId, String targetCompId) {
        validateEdge(srcCompId, targetCompId, StatusCode.WORKFLOW_STREAM_EDGE_INVALID);
        graph.addEdge(srcCompId, targetCompId);
        if (graph instanceof PregelGraph pregelGraph) {
            Vertex targetVertex = pregelGraph.getVertex(targetCompId);
            if (targetVertex != null) {
                streamActor.addStreamConsumer(targetVertex, targetCompId);
            }
        }
        workflowSpec.getStreamEdges()
                .computeIfAbsent(srcCompId, k -> new ArrayList<>())
                .add(targetCompId);
        return this;
    }

    @SuppressWarnings("unchecked")
    public BaseWorkflow addConditionalConnection(String srcCompId, Object router) {
        if (srcCompId == null || srcCompId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_CONDITION_EDGE_INVALID,
                    "src_comp_id", srcCompId,
                    "reason", "src_comp_id cannot be empty or None");
        }
        if (router == null) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_CONDITION_EDGE_INVALID,
                    "src_comp_id", srcCompId,
                    "reason", "router function is required for conditional edges");
        }

        if (router instanceof BranchRouter) {
            ((BranchRouter) router).setSession(session);
            graph.addConditionalEdges(srcCompId, router);
        } else if (router instanceof Function) {
            Function<Object, Object> routerFunc = (Function<Object, Object>) router;
            RouterSession routerSessionWrapper = new RouterSession(session);
            Function<Object, Object> newRouter = state -> routerFunc.apply(routerSessionWrapper);
            graph.addConditionalEdges(srcCompId, (Router) newRouter::apply);
        } else {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_CONDITION_EDGE_INVALID,
                    "src_comp_id", srcCompId,
                    "reason", "router must be a callable function, got " + router.getClass().getSimpleName());
        }
        return this;
    }

    public ExecutableGraph<?, ?> compile(BaseSession sessionArg, Object context) {
        if (sessionArg instanceof WorkflowSession) {
            ((WorkflowSession) sessionArg).setWorkflowId(workflowConfig.getCard().getId());
        }
        if (sessionArg instanceof SubWorkflowSession) {
            Object mainConfig = sessionArg.config().getWorkflowConfig(
                    ((SubWorkflowSession) sessionArg).mainWorkflowId());
            if (mainConfig instanceof WorkflowConfig) {
                WorkflowConfig mwc = (WorkflowConfig) mainConfig;
                if (((SubWorkflowSession) sessionArg).workflowNestingDepth() > mwc.getWorkflowMaxNestingDepth()) {
                    throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR,
                            "reason", "workflow nesting hierarchy is too big, must <= "
                                    + mwc.getWorkflowMaxNestingDepth());
                }
            }
        }
        session.setSession(sessionArg);
        try {
            return graph.compile(sessionArg);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR,
                    "reason", e.getMessage());
        }
    }

    /**
     * Auto-complete component abilities based on edge topology.
     */
    public void autoCompleteAbilities() {
        Map<String, List<String>> sourceMap = workflowSpec.getEdges();
        Map<String, List<String>> sourceStreamMap = workflowSpec.getStreamEdges();
        Map<String, List<String>> targetMap = invertMap(sourceMap);
        Map<String, List<String>> targetStreamMap = invertMap(sourceStreamMap);

        // Validate all edge nodes exist
        Set<String> registeredComps = workflowSpec.getCompConfigs().keySet();
        Set<String> allEdgeNodes = new HashSet<>();
        allEdgeNodes.addAll(sourceMap.keySet());
        allEdgeNodes.addAll(targetMap.keySet());
        allEdgeNodes.addAll(sourceStreamMap.keySet());
        allEdgeNodes.addAll(targetStreamMap.keySet());
        allEdgeNodes.removeAll(registeredComps);
        if (!allEdgeNodes.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR,
                    "reason", "Component ID mismatch: nodes " + allEdgeNodes
                            + " are referenced in edges but not registered via addWorkflowComp");
        }

        // Identify user-provided abilities
        Map<String, Boolean> userProvided = new HashMap<>();
        for (Map.Entry<String, NodeConfig> entry : workflowSpec.getCompConfigs().entrySet()) {
            userProvided.put(entry.getKey(), !entry.getValue().getAbilities().isEmpty());
        }

        // Complete stream node abilities
        for (String node : sourceStreamMap.keySet()) {
            if (Boolean.TRUE.equals(userProvided.get(node))) {
                continue;
            }
            if (targetMap.containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.STREAM);
            }
            if (targetStreamMap.containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.TRANSFORM);
            } else if (!workflowSpec.getStartNodes().contains(node)) {
                addAbilityToNode(node, ComponentAbility.STREAM);
            }
        }
        for (String node : targetStreamMap.keySet()) {
            if (Boolean.TRUE.equals(userProvided.get(node))) {
                continue;
            }
            if (sourceMap.containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.COLLECT);
            }
        }

        // Complete invoke abilities
        for (String node : targetMap.keySet()) {
            if (!Boolean.TRUE.equals(userProvided.get(node)) && sourceMap.containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.INVOKE);
            }
        }
    }

    // ======================= Validation Methods =======================

    private void validateCompId(String compId) {
        if (compId == null || compId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID,
                    "comp_id", compId, "reason", "is None or empty");
        }
        if (compId.length() > 100) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID,
                    "comp_id", compId, "reason", "length must not between [1, 100]");
        }
        if (!COMP_ID_PATTERN.matcher(compId).matches()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID,
                    "comp_id", compId,
                    "reason", "only support letters (a-z, A-Z), digits (0-9), underscores (_) or hyphens (-)");
        }
    }

    private void validateCompAbility(String compId, List<ComponentAbility> abilities, Boolean waitForAll) {
        if (abilities == null) {
            return;
        }
        for (ComponentAbility ability : abilities) {
            if (ability == ComponentAbility.TRANSFORM || ability == ComponentAbility.COLLECT) {
                boolean wait = waitForAll != null && waitForAll;
                if (!wait) {
                    throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ABILITY_INVALID,
                            "comp_id", compId,
                            "reason", "stream components (TRANSFORM/COLLECT) must set 'wait_for_all' to True");
                }
            }
        }
    }

    private void validateEdge(Object srcCompId, String targetCompId, StatusCode errorCode) {
        if (srcCompId == null || (srcCompId instanceof String && ((String) srcCompId).isEmpty())) {
            throw ErrorHelper.buildError(errorCode,
                    "src_comp_id", String.valueOf(srcCompId),
                    "target_comp_id", targetCompId,
                    "reason", "src_comp_id cannot be empty or None");
        }
        if (targetCompId == null || targetCompId.isEmpty()) {
            throw ErrorHelper.buildError(errorCode,
                    "src_comp_id", String.valueOf(srcCompId),
                    "target_comp_id", targetCompId,
                    "reason", "target_comp_id cannot be empty or None");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateSchemas(String compId, Object inputsSchema, Object outputsSchema,
                                 Object streamInputsSchema, Object streamOutputsSchema) {
        // No overlap validation needed unless both are dicts
        // This matches Python's _validate_schemas logic
    }

    // ======================= Helper Methods =======================

    private void addAbilityToNode(String compId, ComponentAbility ability) {
        NodeConfig config = workflowSpec.getCompConfigs().get(compId);
        if (config != null && !config.getAbilities().contains(ability)) {
            config.getAbilities().add(ability);
        }
    }

    private static Map<String, List<String>> invertMap(Map<String, List<String>> sourceMap) {
        Map<String, List<String>> targetMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : sourceMap.entrySet()) {
            for (String target : entry.getValue()) {
                targetMap.computeIfAbsent(target, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        return targetMap;
    }
}
