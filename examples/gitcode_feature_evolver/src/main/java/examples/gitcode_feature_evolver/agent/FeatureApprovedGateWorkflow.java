/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.Start;
import examples.gitcode_feature_evolver.job.ApprovedGateReceipt;
import examples.gitcode_feature_evolver.job.FeatureFailure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registers the stage-bound, zero-argument {@code runApprovedGate} Workflow.
 *
 * @since 0.1.12
 */
final class FeatureApprovedGateWorkflow {
    static final String NAME = "runApprovedGate";

    private FeatureApprovedGateWorkflow() {
    }

    static Registration register(ReActAgent agent, String agentId, GateInvoker invoker) {
        Objects.requireNonNull(agent, "agent must not be null");
        String workflowId = agentId + ".approved_gate";
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(NAME)
                .description("Run the immutable Controller-approved validation for this stage")
                .inputParams(inputSchema())
                .build();
        Runner.resourceMgr().removeWorkflow(workflowId, agentId,
                TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addWorkflow(card,
                () -> workflow(card, Objects.requireNonNull(invoker)), agentId);
        agent.getAbilityManager().add(card);
        return new Registration(workflowId, agentId);
    }

    static Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(),
                "additionalProperties", false);
    }

    private static Workflow workflow(WorkflowCard card, GateInvoker invoker) {
        Workflow workflow = new Workflow(card);
        workflow.setStartComp("start", new Start(), Map.of(), null);
        workflow.setEndComp("gate", new GateComponent(invoker), Map.of(), null);
        workflow.addConnection("start", "gate");
        return workflow;
    }

    private static Map<String, Object> response(ApprovedGateReceipt receipt) {
        ApprovedGateReceipt.Result result = receipt.result();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.status().name());
        response.put("stage", receipt.stage().name());
        response.put("profile", receipt.identity().profile());
        response.put("fingerprint", receipt.identity().fingerprint());
        response.put("cached", result.cached());
        response.put("failure", result.failure().map(FeatureApprovedGateWorkflow::failure)
                .orElse(null));
        response.put("evidence", Map.of("exitCode", result.evidence().exitCode(),
                "outputTail", result.evidence().outputTail()));
        return response;
    }

    private static Map<String, Object> failure(FeatureFailure failure) {
        String details = failure.diagnostic().details();
        String hint = details.substring(0, Math.min(details.length(), 2_000));
        return Map.of("code", failure.code(), "category", failure.category().name(),
                "summary", failure.diagnostic().summary(), "repairHints",
                hint.isBlank() ? java.util.List.of() : java.util.List.of(hint));
    }

    @FunctionalInterface
    interface GateInvoker {
        /** @return current immutable-input Gate receipt */
        ApprovedGateReceipt run();
    }

    /** Removes the stage-only Workflow after the complete Agent lifecycle. */
    record Registration(String workflowId, String agentId) implements AutoCloseable {
        @Override
        public void close() {
            Runner.resourceMgr().removeWorkflow(workflowId, agentId,
                    TagMatchStrategy.ALL, true);
        }
    }

    private static final class GateComponent extends WorkflowComponent {
        private final GateInvoker invoker;

        private GateComponent(GateInvoker invoker) {
            this.invoker = invoker;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return response(invoker.run());
        }
    }
}
