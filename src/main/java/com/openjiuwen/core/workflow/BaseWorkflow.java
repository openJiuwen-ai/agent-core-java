/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.graph.*;
import com.openjiuwen.core.graph.stream_actor.StreamGraph;
import com.openjiuwen.core.graph.visualization.Drawable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.ProxySession;
import com.openjiuwen.core.session.internal.RouterSession;
import com.openjiuwen.core.session.internal.SubWorkflowSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.IOConfig;
import com.openjiuwen.core.workflow.component.NodeConfig;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Base workflow implementation providing graph construction, edge management,
 * component configuration, and ability inference.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow._workflow.BaseWorkflow}.
 */
public class BaseWorkflow implements HasDrawable {

    private static final Pattern COMP_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final String WORKFLOW_DRAWABLE = "WORKFLOW_DRAWABLE";

    private final Graph graph;
    private final WorkflowConfig workflowConfig;
    private final WorkflowSpec workflowSpec;
    private final StreamGraph streamActor;
    private final ProxySession session;
    private final Drawable drawable;

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
        this.drawable = isDrawableEnabled() ? new Drawable() : null;
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
        if (drawable != null) {
            drawable.addNode(compId, workflowComp);
        }

        return this;
    }

    public BaseWorkflow startComp(String startCompId) {
        validateCompId(startCompId);
        graph.startNode(startCompId);
        if (drawable != null) {
            drawable.setStartNode(startCompId);
        }
        workflowSpec.getStartNodes().add(startCompId);
        return this;
    }

    public BaseWorkflow endComp(String endCompId) {
        validateCompId(endCompId);
        graph.endNode(endCompId);
        if (drawable != null) {
            drawable.setEndNode(endCompId);
        }
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
                if (drawable != null) {
                    drawable.addEdge(sourceId, targetCompId);
                }
            }
        } else {
            workflowSpec.getEdges()
                    .computeIfAbsent((String) srcCompId, k -> new ArrayList<>())
                    .add(targetCompId);
            if (drawable != null) {
                drawable.addEdge((String) srcCompId, targetCompId);
            }
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
        if (drawable != null) {
            drawable.addEdge(srcCompId, targetCompId, false, true, null);
        }
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
        if (drawable != null) {
            drawable.addEdge(srcCompId, null, true, false, router);
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
            Map<String, Object> kwargs = context != null ? Map.of("context", context) : null;
            return graph.compile(sessionArg, kwargs);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR,
                    "reason", e.getMessage());
        }
    }

    public String toMermaid(String title, int expandSubgraph, boolean enableAnimation) {
        if (drawable == null) {
            return "";
        }
        return drawable.toMermaid(title, expandSubgraph, enableAnimation);
    }

    public String toMermaid() {
        return toMermaid("", 0, false);
    }

    /**
     * Render the workflow graph as a PNG image.
     * <p>
     * Mirrors Python's {@code BaseWorkflow.to_mermaid_png()}.
     *
     * @param title          diagram title
     * @param expandSubgraph depth of subgraph expansion
     * @return PNG bytes, or empty array if drawable is not enabled
     */
    public byte[] toMermaidPng(String title, int expandSubgraph) {
        if (drawable == null) {
            return new byte[0];
        }
        return drawable.toMermaidPng(title, expandSubgraph);
    }

    public byte[] toMermaidPng() {
        return toMermaidPng("", 0);
    }

    /**
     * Render the workflow graph as an SVG image.
     * <p>
     * Mirrors Python's {@code BaseWorkflow.to_mermaid_svg()}.
     *
     * @param title          diagram title
     * @param expandSubgraph depth of subgraph expansion
     * @return SVG bytes, or empty array if drawable is not enabled
     */
    public byte[] toMermaidSvg(String title, int expandSubgraph) {
        if (drawable == null) {
            return new byte[0];
        }
        return drawable.toMermaidSvg(title, expandSubgraph);
    }

    public byte[] toMermaidSvg() {
        return toMermaidSvg("", 0);
    }

    @Override
    public Drawable getDrawable() {
        return drawable;
    }

    static boolean isDrawableEnabled() {
        String drawableFlag = System.getenv(WORKFLOW_DRAWABLE);
        if (drawableFlag == null || drawableFlag.isBlank()) {
            drawableFlag = System.getProperty(WORKFLOW_DRAWABLE);
        }
        return "true".equalsIgnoreCase(drawableFlag);
    }

    /**
     * Auto-complete component abilities based on edge topology.
     */
    public void autoCompleteAbilities() {
        EdgeTopology edgeTopology = buildEdgeTopology();
        validateEdgeNodes(edgeTopology);

        Map<String, Boolean> userProvided = new HashMap<>();
        for (Map.Entry<String, NodeConfig> entry : workflowSpec.getCompConfigs().entrySet()) {
            userProvided.put(entry.getKey(), !entry.getValue().getAbilities().isEmpty());
        }

        completeLoopNodeAbilities(edgeTopology, userProvided);
        completeStreamNodeAbilities(edgeTopology, userProvided);
        completeInvokeAbilities(edgeTopology, userProvided);
    }

    public void reset() {
        if (graph instanceof PregelGraph pregelGraph) {
            pregelGraph.reset();
        }
    }

    private EdgeTopology buildEdgeTopology() {
        Map<String, List<String>> sourceMap = workflowSpec.getEdges();
        Map<String, List<String>> sourceStreamMap = workflowSpec.getStreamEdges();
        return new EdgeTopology(
                sourceMap,
                invertMap(sourceMap),
                sourceStreamMap,
                invertMap(sourceStreamMap));
    }

    private void validateEdgeNodes(EdgeTopology edgeTopology) {
        Set<String> registeredComps = workflowSpec.getCompConfigs().keySet();
        Set<String> missingNodes = new HashSet<>(edgeTopology.allEdgeNodes());
        missingNodes.removeAll(registeredComps);
        if (missingNodes.isEmpty()) {
            return;
        }

        List<String> edgeDetails = collectProblematicEdges(edgeTopology, missingNodes);
        throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR,
                "reason", "Component ID mismatch: nodes " + missingNodes
                        + " are referenced in edges but not registered via addWorkflowComp/setStartComp/setEndComp. "
                        + "Registered components: " + registeredComps
                        + ". Problematic edges: " + edgeDetails,
                "workflow", workflowConfig.getCard().str());
    }

    private List<String> collectProblematicEdges(EdgeTopology edgeTopology, Set<String> missingNodes) {
        List<String> edgeDetails = new ArrayList<>();
        collectProblematicEdges(edgeDetails, edgeTopology.getSourceMap(), missingNodes, ConnectionType.CONNECTION);
        collectProblematicEdges(edgeDetails, edgeTopology.getSourceStreamMap(), missingNodes,
                ConnectionType.STREAM_CONNECTION);
        return edgeDetails;
    }

    private void collectProblematicEdges(List<String> edgeDetails,
                                         Map<String, List<String>> edgeMap,
                                         Set<String> missingNodes,
                                         ConnectionType connectionType) {
        for (Map.Entry<String, List<String>> entry : edgeMap.entrySet()) {
            for (String target : entry.getValue()) {
                if (missingNodes.contains(entry.getKey()) || missingNodes.contains(target)) {
                    edgeDetails.add(connectionType.getValue() + ": '" + entry.getKey() + "' -> '" + target + "'");
                }
            }
        }
    }

    private void completeLoopNodeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided) {
        List<String> loopStartNodes = this instanceof LoopGroup loopGroup ? loopGroup.getStartNodesList() : List.of();
        List<String> loopEndNodes = this instanceof LoopGroup loopGroup ? loopGroup.getEndNodesList() : List.of();

        for (String node : loopStartNodes) {
            if (Boolean.TRUE.equals(userProvided.get(node))) {
                continue;
            }
            if (edgeTopology.getSourceStreamMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.STREAM);
            }
            if (edgeTopology.getSourceMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.INVOKE);
            }
        }

        for (String node : loopEndNodes) {
            if (Boolean.TRUE.equals(userProvided.get(node))) {
                continue;
            }
            if (edgeTopology.getTargetStreamMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.COLLECT);
            }
        }
    }

    private void completeStreamNodeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided) {
        for (String node : edgeTopology.getSourceStreamMap().keySet()) {
            if (Boolean.TRUE.equals(userProvided.get(node))) {
                continue;
            }
            if (edgeTopology.getTargetMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.STREAM);
            }
            if (edgeTopology.getTargetStreamMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.TRANSFORM);
            } else if (!workflowSpec.getStartNodes().contains(node)) {
                addAbilityToNode(node, ComponentAbility.STREAM);
            }
        }

        for (String node : edgeTopology.getTargetStreamMap().keySet()) {
            if (Boolean.TRUE.equals(userProvided.get(node))) {
                continue;
            }
            if (edgeTopology.getSourceMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.COLLECT);
            }
        }
    }

    private void completeInvokeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided) {
        for (String node : edgeTopology.getTargetMap().keySet()) {
            if (!Boolean.TRUE.equals(userProvided.get(node))
                    && edgeTopology.getSourceMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.INVOKE);
            }
        }
    }

    // ======================= Validation Methods =======================

    private void validateCompId(String compId) {
        if (compId == null || compId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID,
                    "comp_id", compId, "reason", "is None or empty",
                    "workflow", workflowConfig.getCard().str());
        }
        if (compId.length() > 100) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID,
                    "comp_id", compId, "reason", "length must not between [1, 100]",
                    "workflow", workflowConfig.getCard().str());
        }
        if (!COMP_ID_PATTERN.matcher(compId).matches()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID,
                    "comp_id", compId,
                    "reason", "only support letters (a-z, A-Z), digits (0-9), underscores (_) or hyphens (-)",
                    "workflow", workflowConfig.getCard().str());
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
                            "reason", "stream components (TRANSFORM/COLLECT) must set 'wait_for_all' to True",
                            "workflow", workflowConfig.getCard().str());
                }
            }
        }
    }

    private void validateEdge(Object srcCompId, String targetCompId, StatusCode errorCode) {
        if (srcCompId == null || (srcCompId instanceof String && ((String) srcCompId).isEmpty())) {
            throw ErrorHelper.buildError(errorCode,
                    "src_cmp_id", String.valueOf(srcCompId),
                    "target_cmp_id", targetCompId,
                    "reason", "src_comp_id cannot be empty or None",
                    "workflow", workflowConfig.getCard().str());
        }
        if (srcCompId instanceof List<?> list) {
            for (int idx = 0; idx < list.size(); idx++) {
                Object value = list.get(idx);
                if (!(value instanceof String strValue) || strValue.isEmpty()) {
                    throw ErrorHelper.buildError(errorCode,
                            "src_cmp_id", String.valueOf(srcCompId),
                            "target_cmp_id", targetCompId,
                            "reason", "src_comp_id list contains invalid value at index " + idx,
                            "workflow", workflowConfig.getCard().str());
                }
            }
        } else if (!(srcCompId instanceof String)) {
            throw ErrorHelper.buildError(errorCode,
                    "src_cmp_id", String.valueOf(srcCompId),
                    "target_cmp_id", targetCompId,
                    "reason", "src_comp_id must be a string or list[string]",
                    "workflow", workflowConfig.getCard().str());
        }
        if (targetCompId == null || targetCompId.isEmpty()) {
            throw ErrorHelper.buildError(errorCode,
                    "src_cmp_id", String.valueOf(srcCompId),
                    "target_cmp_id", targetCompId,
                    "reason", "target_comp_id cannot be empty or None",
                    "workflow", workflowConfig.getCard().str());
        }
    }

    @SuppressWarnings("unchecked")
    private void validateSchemas(String compId, Object inputsSchema, Object outputsSchema,
                                 Object streamInputsSchema, Object streamOutputsSchema) {
        validateSchemaOverlap(compId, inputsSchema, streamInputsSchema,
                "inputs_schema", "stream_inputs_schema");
        validateSchemaOverlap(compId, outputsSchema, streamOutputsSchema,
                "outputs_schema", "stream_outputs_schema");
    }

    @SuppressWarnings("unchecked")
    private void validateSchemaOverlap(String compId, Object firstSchema, Object secondSchema,
                                       String firstName, String secondName) {
        if (!(firstSchema instanceof Map<?, ?> firstMapRaw) || !(secondSchema instanceof Map<?, ?> secondMapRaw)) {
            return;
        }
        Map<String, Object> firstMap = (Map<String, Object>) firstMapRaw;
        Map<String, Object> secondMap = (Map<String, Object>) secondMapRaw;
        Map<String, Object> flattenedFirst = DictUtils.flattenMap(firstMap);
        Map<String, Object> flattenedSecond = DictUtils.flattenMap(secondMap);
        for (String key : flattenedFirst.keySet()) {
            if (flattenedSecond.containsKey(key)) {
                throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_SCHEMA_INVALID,
                        "comp_id", compId,
                        "reason", "duplicate key both exist in " + firstName + " with " + secondName
                                + ", key=" + key,
                        "workflow", workflowConfig.getCard().str());
            }
        }
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
