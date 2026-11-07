/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.schema.DesignerNodeState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapts one designer node to a core Pregel executable.
 *
 * @since 2026-11-06
 */
final class DesignerNodeExecutable extends Executable<Map<String, Object>, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DesignerNodeExecutable.class);

    private final DesignerNode node;
    private final DesignerRunContext context;
    private final boolean frozen;

    DesignerNodeExecutable(DesignerNode node, DesignerRunContext context, boolean frozen) {
        this.node = node;
        this.context = context;
        this.frozen = frozen;
    }

    @Override
    public Object onInvoke(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
        LOG.info("Designer node start: id={}, type={}, pipeline={}, frozen={}",
                node.id(), node.type(), node.config().get("pipeline"), frozen);
        if (frozen) {
            NodeOutcome outcome = frozenOutcome();
            context.record(node.id(), outcome);
            LOG.info("Designer node frozen: id={}, status={}", node.id(), outcome.status());
            return outcome;
        }
        if (context.isCancelled()) {
            LOG.info("Designer node cancelled before start: id={}", node.id());
            return NodeOutcome.skipped(List.of(), "designer run cancelled");
        }
        List<String> blockedBy = blockedBy();
        if (!blockedBy.isEmpty()) {
            NodeOutcome outcome = NodeOutcome.skipped(blockedBy,
                    "upstream node failed: " + String.join(", ", blockedBy) + " - node not executed");
            context.record(node.id(), outcome);
            LOG.warn("Designer node skipped: id={}, blockedBy={}", node.id(), blockedBy);
            return outcome;
        }
        Optional<NodeHandler> handler = context.handlers().resolve(node);
        if (handler.isEmpty()) {
            NodeOutcome outcome = NodeOutcome.failed("no handler for node " + node.id());
            context.record(node.id(), outcome);
            LOG.warn("Designer node has no handler: id={}, pipeline={}", node.id(),
                    node.config().get("pipeline"));
            return outcome;
        }
        long startedAt = System.nanoTime();
        context.markStarted(node.id());
        NodeOutcome outcome;
        try {
            outcome = handler.get().execute(node, context);
        } catch (DesignerNodeException ex) {
            outcome = NodeOutcome.failed(ex.getMessage());
        }
        if (outcome == null) {
            outcome = NodeOutcome.failed("node " + node.id() + " returned no outcome");
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        context.record(node.id(), outcome);
        if (!outcome.isCompleted()) {
            LOG.warn("Designer node ended {}: id={}, durationMs={}, error={}",
                    outcome.status(), node.id(), durationMs, outcome.error());
        } else {
            LOG.info("Designer node completed: id={}, durationMs={}, output={}",
                    node.id(), durationMs, outcome.outputRef() == null ? ""
                            : outcome.outputRef().uri());
        }
        context.markFinished(node.id(), outcome);
        return outcome;
    }

    private NodeOutcome frozenOutcome() {
        DesignerNodeState state = context.run().nodeStates().get(node.id());
        if (state == null) {
            return NodeOutcome.failed("frozen node " + node.id() + " has no stored output");
        }
        return new NodeOutcome("completed", state.text(), state.outputRef(), state.outputRefs(),
                null, List.of());
    }

    private List<String> blockedBy() {
        List<String> blocked = new ArrayList<>();
        for (var edge : context.graph().edges()) {
            if (!edge.target().equals(node.id())) {
                continue;
            }
            Optional<NodeOutcome> upstream = context.outcome(edge.source());
            if (upstream.isEmpty() || !upstream.get().isCompleted()) {
                blocked.add(edge.source());
            }
        }
        return blocked;
    }
}
