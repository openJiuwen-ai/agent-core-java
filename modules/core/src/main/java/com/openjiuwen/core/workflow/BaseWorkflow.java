/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.graph.ExecutableGraph;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.graph.Router;
import com.openjiuwen.core.graph.Vertex;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * 
 * @since 0.1.7
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

    /**
     * BaseWorkflow.
     * 
     * @since 0.1.7
     */
    public BaseWorkflow() {
        this(null, null);
    }

    /**
     * BaseWorkflow.
     * 
     * @param workflowConfig workflowConfig
     * @param newGraph newGraph
     * @since 0.1.7
     */
    public BaseWorkflow(WorkflowConfig workflowConfig, Graph newGraph) {
        this.graph = newGraph != null ? newGraph : new PregelGraph();
        this.workflowConfig = workflowConfig != null
                ? workflowConfig
                : new WorkflowConfig(WorkflowCard.builder().id(UUID.randomUUID().toString().replace("-", "")).build());
        this.workflowSpec = this.workflowConfig.getSpec();
        this.streamActor = new StreamGraph();
        this.session = new ProxySession();
        this.drawable = isDrawableEnabled() ? new Drawable() : null;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowConfig getConfig() {
        return workflowConfig;
    }

    /**
     * getGraph.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Graph getGraph() {
        return graph;
    }

    /**
     * getStreamActor.
     * 
     * @return the result
     * @since 0.1.7
     */
    public StreamGraph getStreamActor() {
        return streamActor;
    }

    /**
     * Add a workflow component with full configuration.
     * 
     * @param compId compId
     * @param workflowComp workflowComp
     * @param waitForAll waitForAll
     * @param inputsSchema inputsSchema
     * @param outputsSchema outputsSchema
     * @param streamInputsSchema streamInputsSchema
     * @param streamOutputsSchema streamOutputsSchema
     * @param compAbility compAbility
     * @return the result
     * @since 0.1.7
     */
    public BaseWorkflow addWorkflowComp(String compId, ComponentComposable workflowComp, Boolean waitForAll,
            Object inputsSchema, Object outputsSchema, Object streamInputsSchema, Object streamOutputsSchema,
            List<ComponentAbility> compAbility) {
        validateCompId(compId);
        validateSchemas(compId, inputsSchema, outputsSchema, streamInputsSchema, streamOutputsSchema);
        validateCompAbility(compId, compAbility, waitForAll);

        NodeConfig nodeSpec = new NodeConfig(compAbility != null ? new ArrayList<>(compAbility) : new ArrayList<>(),
                new IOConfig(inputsSchema, outputsSchema), new IOConfig(streamInputsSchema, streamOutputsSchema));

        workflowSpec.getCompConfigs().put(compId, nodeSpec);

        boolean wait = waitForAll != null && waitForAll;
        workflowComp.addComponent(graph, compId, wait);
        if (drawable != null) {
            drawable.addNode(compId, workflowComp);
        }

        return this;
    }

    /**
     * startComp.
     * 
     * @param startCompId startCompId
     * @return the result
     * @since 0.1.7
     */
    public BaseWorkflow startComp(String startCompId) {
        validateCompId(startCompId);
        graph.startNode(startCompId);
        if (drawable != null) {
            drawable.setStartNode(startCompId);
        }
        workflowSpec.getStartNodes().add(startCompId);
        return this;
    }

    /**
     * endComp.
     * 
     * @param endCompId endCompId
     * @return the result
     * @since 0.1.7
     */
    public BaseWorkflow endComp(String endCompId) {
        validateCompId(endCompId);
        graph.endNode(endCompId);
        if (drawable != null) {
            drawable.setEndNode(endCompId);
        }
        return this;
    }

    /**
     * addConnection.
     * 
     * @param srcCompId srcCompId
     * @param targetCompId targetCompId
     * @return the result
     * @since 0.1.7
     */
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
            workflowSpec.getEdges().computeIfAbsent((String) srcCompId, k -> new ArrayList<>()).add(targetCompId);
            if (drawable != null) {
                drawable.addEdge((String) srcCompId, targetCompId);
            }
        }
        return this;
    }

    /**
     * addStreamConnection.
     * 
     * @param srcCompId srcCompId
     * @param targetCompId targetCompId
     * @return the result
     * @since 0.1.7
     */
    public BaseWorkflow addStreamConnection(String srcCompId, String targetCompId) {
        validateEdge(srcCompId, targetCompId, StatusCode.WORKFLOW_STREAM_EDGE_INVALID);
        graph.addEdge(srcCompId, targetCompId);
        if (graph instanceof PregelGraph pregelGraph) {
            Vertex targetVertex = pregelGraph.getVertex(targetCompId);
            if (targetVertex != null) {
                streamActor.addStreamConsumer(targetVertex, targetCompId);
            }
        }
        workflowSpec.getStreamEdges().computeIfAbsent(srcCompId, k -> new ArrayList<>()).add(targetCompId);
        if (drawable != null) {
            drawable.addEdge(srcCompId, targetCompId, false, true, null);
        }
        return this;
    }

    /**
     * addConditionalConnection.
     * 
     * @param srcCompId srcCompId
     * @param router router
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public BaseWorkflow addConditionalConnection(String srcCompId, Object router) {
        if (srcCompId == null || srcCompId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_CONDITION_EDGE_INVALID, "src_comp_id", srcCompId, "reason",
                    "src_comp_id cannot be empty or None");
        }
        if (router == null) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_CONDITION_EDGE_INVALID, "src_comp_id", srcCompId, "reason",
                    "router function is required for conditional edges");
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
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_CONDITION_EDGE_INVALID, "src_comp_id", srcCompId, "reason",
                    "router must be a callable function, got " + router.getClass().getSimpleName());
        }
        if (drawable != null) {
            drawable.addEdge(srcCompId, null, true, false, router);
        }
        return this;
    }

    /**
     * compile.
     * 
     * @param sessionArg sessionArg
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    public ExecutableGraph<?, ?> compile(BaseSession sessionArg, Object context) {
        if (sessionArg instanceof WorkflowSession) {
            ((WorkflowSession) sessionArg).setWorkflowId(workflowConfig.getCard().getId());
        }
        if (sessionArg instanceof SubWorkflowSession) {
            Object mainConfig =
                sessionArg.config().getWorkflowConfig(((SubWorkflowSession) sessionArg).mainWorkflowId());
            if (mainConfig instanceof WorkflowConfig) {
                WorkflowConfig mwc = (WorkflowConfig) mainConfig;
                if (((SubWorkflowSession) sessionArg).workflowNestingDepth() > mwc.getWorkflowMaxNestingDepth()) {
                    throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR, "reason",
                            "workflow nesting hierarchy is too big, must <= " + mwc.getWorkflowMaxNestingDepth());
                }
            }
        }
        session.setSession(sessionArg);
        try {
            Map<String, Object> kwargs = context != null ? Map.of("context", context) : null;
            return graph.compile(sessionArg, kwargs);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR, "reason", e.getMessage());
        }
    }

    /**
     * toMermaid.
     * 
     * @param title title
     * @param expandSubgraph expandSubgraph
     * @param enableAnimation enableAnimation
     * @return the result
     * @since 0.1.7
     */
    public String toMermaid(String title, int expandSubgraph, boolean enableAnimation) {
        if (drawable == null) {
            return "";
        }
        return drawable.toMermaid(title, expandSubgraph, enableAnimation);
    }

    /**
     * toMermaid.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String toMermaid() {
        return toMermaid("", 0, false);
    }

    /**
     * Render the workflow graph as a PNG image.
     * <p>
     * Mirrors Python's {@code BaseWorkflow.to_mermaid_png()}.
     * 
     * @param title diagram title
     * @param expandSubgraph depth of subgraph expansion
     * @return PNG bytes, or empty array if drawable is not enabled
     * @since 0.1.7
     */
    public byte[] toMermaidPng(String title, int expandSubgraph) {
        if (drawable == null) {
            return new byte[0];
        }
        return drawable.toMermaidPng(title, expandSubgraph);
    }

    /**
     * toMermaidPng.
     * 
     * @return the result
     * @since 0.1.7
     */
    public byte[] toMermaidPng() {
        return toMermaidPng("", 0);
    }

    /**
     * Render the workflow graph as an SVG image.
     * <p>
     * Mirrors Python's {@code BaseWorkflow.to_mermaid_svg()}.
     * 
     * @param title diagram title
     * @param expandSubgraph depth of subgraph expansion
     * @return SVG bytes, or empty array if drawable is not enabled
     * @since 0.1.7
     */
    public byte[] toMermaidSvg(String title, int expandSubgraph) {
        if (drawable == null) {
            return new byte[0];
        }
        return drawable.toMermaidSvg(title, expandSubgraph);
    }

    /**
     * toMermaidSvg.
     * 
     * @return the result
     * @since 0.1.7
     */
    public byte[] toMermaidSvg() {
        return toMermaidSvg("", 0);
    }

    /**
     * getDrawable.
     * 
     * @return the result
     * @since 0.1.7
     */
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
     * 
     * @since 0.1.7
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
        completeStreamSourceGroups(edgeTopology);
    }

    /**
     * Build stream consumer source groups aligned with Pregel barrier groups.
     * Mirrors Python {@code BaseWorkflow._complete_stream_source_groups}.
     *
     * <p>Java has no branch-target resolver yet, so each producer-id group is
     * used as-is. For a single-producer group, the group expands to one source
     * per STREAM/TRANSFORM ability of that producer (COLLECT is excluded — it
     * is a batch-out ability and never produces stream chunks on the wire).
     *
     * @param edgeTopology edgeTopology
     * @since 0.1.7
     */
    private void completeStreamSourceGroups(EdgeTopology edgeTopology) {
        Map<String, List<Set<String>>> sourceGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : edgeTopology.getTargetStreamMap().entrySet()) {
            List<Set<String>> streamGroups = buildStreamGroupsForConsumer(entry.getValue());
            if (!streamGroups.isEmpty()) {
                sourceGroups.put(entry.getKey(), streamGroups);
            }
        }
        workflowSpec.setStreamSourceGroups(sourceGroups);
    }

    /**
     * Build the list of CNF OR-groups for one consumer. Each producer-id group
     * from the edge topology becomes one OR-group. A single-producer group is
     * expanded into one OR-group per STREAM/TRANSFORM ability of that producer
     * (COLLECT is excluded — it is a batch-out ability and never produces stream
     * chunks on the wire). A multi-producer group is collapsed into a single
     * multi-source OR-group.
     *
     * @param producerIds producerIds
     * @return the result
     * @since 0.1.7
     */
    private List<Set<String>> buildStreamGroupsForConsumer(List<String> producerIds) {
        List<Set<String>> streamGroups = new ArrayList<>();
        for (String producerId : producerIds) {
            List<Set<String>> abilityGroups = collectStreamAbilityGroups(producerId);
            streamGroups.addAll(abilityGroups);
        }
        return streamGroups;
    }

    /**
     * Collect STREAM/TRANSFORM source keys (producer-id + ability name) for a
     * single producer. Each ability becomes its own single-source OR-group, so
     * the result is a list of singletons rather than one combined set. Returns
     * an empty list when the producer has no such abilities or is not in the
     * workflow spec.
     *
     * @param producerId producerId
     * @return the result
     * @since 0.1.7
     */
    private List<Set<String>> collectStreamAbilityGroups(String producerId) {
        List<Set<String>> abilityGroups = new ArrayList<>();
        NodeConfig producerConfig = workflowSpec.getCompConfigs().get(producerId);
        if (producerConfig != null && producerConfig.getAbilities() != null) {
            for (ComponentAbility ability : producerConfig.getAbilities()) {
                if (ability == ComponentAbility.STREAM || ability == ComponentAbility.TRANSFORM) {
                    Set<String> singleAbility = new LinkedHashSet<>();
                    singleAbility.add(producerId + "-" + ability.name());
                    abilityGroups.add(singleAbility);
                }
            }
        }
        return abilityGroups;
    }

    /**
     * reset.
     * 
     * @since 0.1.7
     */
    public void reset() {
        if (graph instanceof PregelGraph pregelGraph) {
            pregelGraph.reset();
        }
    }

    /**
     * buildEdgeTopology.
     * 
     * @return the result
     * @since 0.1.7
     */
    private EdgeTopology buildEdgeTopology() {
        Map<String, List<String>> sourceMap = workflowSpec.getEdges();
        Map<String, List<String>> sourceStreamMap = workflowSpec.getStreamEdges();
        return new EdgeTopology(sourceMap, invertMap(sourceMap), sourceStreamMap, invertMap(sourceStreamMap));
    }

    /**
     * validateEdgeNodes.
     * 
     * @param edgeTopology edgeTopology
     * @since 0.1.7
     */
    private void validateEdgeNodes(EdgeTopology edgeTopology) {
        Set<String> registeredComps = workflowSpec.getCompConfigs().keySet();
        Set<String> missingNodes = new HashSet<>(edgeTopology.allEdgeNodes());
        missingNodes.removeAll(registeredComps);
        if (missingNodes.isEmpty()) {
            return;
        }

        List<String> edgeDetails = collectProblematicEdges(edgeTopology, missingNodes);
        throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPILE_ERROR, "reason",
                "Component ID mismatch: nodes " + missingNodes
                        + " are referenced in edges but not registered via addWorkflowComp/setStartComp/setEndComp. "
                        + "Registered components: " + registeredComps + ". Problematic edges: " + edgeDetails,
                "workflow", workflowConfig.getCard().str());
    }

    /**
     * collectProblematicEdges.
     * 
     * @param edgeTopology edgeTopology
     * @param missingNodes missingNodes
     * @return the result
     * @since 0.1.7
     */
    private List<String> collectProblematicEdges(EdgeTopology edgeTopology, Set<String> missingNodes) {
        List<String> edgeDetails = new ArrayList<>();
        collectProblematicEdges(edgeDetails, edgeTopology.getSourceMap(), missingNodes, ConnectionType.CONNECTION);
        collectProblematicEdges(edgeDetails, edgeTopology.getSourceStreamMap(), missingNodes,
                ConnectionType.STREAM_CONNECTION);
        return edgeDetails;
    }

    /**
     * collectProblematicEdges.
     * 
     * @param edgeDetails edgeDetails
     * @param edgeMap edgeMap
     * @param missingNodes missingNodes
     * @param connectionType connectionType
     * @since 0.1.7
     */
    private void collectProblematicEdges(List<String> edgeDetails, Map<String, List<String>> edgeMap,
            Set<String> missingNodes, ConnectionType connectionType) {
        for (Map.Entry<String, List<String>> entry : edgeMap.entrySet()) {
            for (String target : entry.getValue()) {
                if (missingNodes.contains(entry.getKey()) || missingNodes.contains(target)) {
                    edgeDetails.add(connectionType.getValue() + ": '" + entry.getKey() + "' -> '" + target + "'");
                }
            }
        }
    }

    /**
     * completeLoopNodeAbilities.
     * 
     * @param edgeTopology edgeTopology
     * @param userProvided userProvided
     * @since 0.1.7
     */
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

    /**
     * completeStreamNodeAbilities.
     * 
     * @param edgeTopology edgeTopology
     * @param userProvided userProvided
     * @since 0.1.7
     */
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
            } else {
                // no-op
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

    /**
     * completeInvokeAbilities.
     * 
     * @param edgeTopology edgeTopology
     * @param userProvided userProvided
     * @since 0.1.7
     */
    private void completeInvokeAbilities(EdgeTopology edgeTopology, Map<String, Boolean> userProvided) {
        for (String node : edgeTopology.getTargetMap().keySet()) {
            if (!Boolean.TRUE.equals(userProvided.get(node)) && edgeTopology.getSourceMap().containsKey(node)) {
                addAbilityToNode(node, ComponentAbility.INVOKE);
            }
        }
    }

    // ======================= Validation Methods =======================

    /**
     * validateCompId.
     * 
     * @param compId compId
     * @since 0.1.7
     */
    private void validateCompId(String compId) {
        if (compId == null || compId.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID, "comp_id", compId, "reason",
                    "is None or empty", "workflow", workflowConfig.getCard().str());
        }
        if (compId.length() > 100) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID, "comp_id", compId, "reason",
                    "length must not between [1, 100]", "workflow", workflowConfig.getCard().str());
        }
        if (!COMP_ID_PATTERN.matcher(compId).matches()) {
            throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ID_INVALID, "comp_id", compId, "reason",
                    "only support letters (a-z, A-Z), digits (0-9), underscores (_) or hyphens (-)", "workflow",
                    workflowConfig.getCard().str());
        }
    }

    /**
     * validateCompAbility.
     * 
     * @param compId compId
     * @param abilities abilities
     * @param waitForAll waitForAll
     * @since 0.1.7
     */
    private void validateCompAbility(String compId, List<ComponentAbility> abilities, Boolean waitForAll) {
        if (abilities == null) {
            return;
        }
        for (ComponentAbility ability : abilities) {
            if (ability == ComponentAbility.TRANSFORM || ability == ComponentAbility.COLLECT) {
                boolean wait = waitForAll != null && waitForAll;
                if (!wait) {
                    throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_ABILITY_INVALID, "comp_id", compId,
                            "reason", "stream components (TRANSFORM/COLLECT) must set 'wait_for_all' to True",
                            "workflow", workflowConfig.getCard().str());
                }
            }
        }
    }

    /**
     * validateEdge.
     * 
     * @param srcCompId srcCompId
     * @param targetCompId targetCompId
     * @param errorCode errorCode
     * @since 0.1.7
     */
    private void validateEdge(Object srcCompId, String targetCompId, StatusCode errorCode) {
        if (srcCompId == null || (srcCompId instanceof String && ((String) srcCompId).isEmpty())) {
            throw ErrorHelper.buildError(errorCode, "src_cmp_id", String.valueOf(srcCompId), "target_cmp_id",
                    targetCompId, "reason", "src_comp_id cannot be empty or None", "workflow",
                    workflowConfig.getCard().str());
        }
        if (srcCompId instanceof List<?> list) {
            for (int idx = 0; idx < list.size(); idx++) {
                Object value = list.get(idx);
                if (!(value instanceof String strValue) || strValue.isEmpty()) {
                    throw ErrorHelper.buildError(errorCode, "src_cmp_id", String.valueOf(srcCompId), "target_cmp_id",
                            targetCompId, "reason", "src_comp_id list contains invalid value at index " + idx,
                            "workflow", workflowConfig.getCard().str());
                }
            }
        } else if (!(srcCompId instanceof String)) {
            throw ErrorHelper.buildError(errorCode, "src_cmp_id", String.valueOf(srcCompId), "target_cmp_id",
                    targetCompId, "reason", "src_comp_id must be a string or list[string]", "workflow",
                    workflowConfig.getCard().str());
        }
        if (targetCompId == null || targetCompId.isEmpty()) {
            throw ErrorHelper.buildError(errorCode, "src_cmp_id", String.valueOf(srcCompId), "target_cmp_id",
                    targetCompId, "reason", "target_comp_id cannot be empty or None", "workflow",
                    workflowConfig.getCard().str());
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * validateSchemas.
     * 
     * @param compId compId
     * @param inputsSchema inputsSchema
     * @param outputsSchema outputsSchema
     * @param streamInputsSchema streamInputsSchema
     * @param streamOutputsSchema streamOutputsSchema
     * @since 0.1.7
     */
    private void validateSchemas(String compId, Object inputsSchema, Object outputsSchema, Object streamInputsSchema,
            Object streamOutputsSchema) {
        validateSchemaOverlap(compId, inputsSchema, streamInputsSchema, "inputs_schema", "stream_inputs_schema");
        validateSchemaOverlap(compId, outputsSchema, streamOutputsSchema, "outputs_schema", "stream_outputs_schema");
    }

    @SuppressWarnings("unchecked")
    /**
     * validateSchemaOverlap.
     * 
     * @param compId compId
     * @param firstSchema firstSchema
     * @param secondSchema secondSchema
     * @param firstName firstName
     * @param secondName secondName
     * @since 0.1.7
     */
    private void validateSchemaOverlap(String compId, Object firstSchema, Object secondSchema, String firstName,
            String secondName) {
        if (!(firstSchema instanceof Map<?, ?> firstMapRaw) || !(secondSchema instanceof Map<?, ?> secondMapRaw)) {
            return;
        }
        Map<String, Object> firstMap = (Map<String, Object>) firstMapRaw;
        Map<String, Object> secondMap = (Map<String, Object>) secondMapRaw;
        Map<String, Object> flattenedFirst = DictUtils.flattenMap(firstMap);
        Map<String, Object> flattenedSecond = DictUtils.flattenMap(secondMap);
        for (String key : flattenedFirst.keySet()) {
            if (flattenedSecond.containsKey(key)) {
                throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_SCHEMA_INVALID, "comp_id", compId, "reason",
                        "duplicate key both exist in " + firstName + " with " + secondName + ", key=" + key, "workflow",
                        workflowConfig.getCard().str());
            }
        }
    }

    // ======================= Helper Methods =======================

    /**
     * addAbilityToNode.
     * 
     * @param compId compId
     * @param ability ability
     * @since 0.1.7
     */
    private void addAbilityToNode(String compId, ComponentAbility ability) {
        NodeConfig config = workflowSpec.getCompConfigs().get(compId);
        if (config != null && !config.getAbilities().contains(ability)) {
            config.getAbilities().add(ability);
        }
    }

    /**
     * invertMap.
     * 
     * @param sourceMap sourceMap
     * @return the result
     * @since 0.1.7
     */
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
