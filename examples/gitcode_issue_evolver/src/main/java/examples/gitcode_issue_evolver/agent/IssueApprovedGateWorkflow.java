/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.agent;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.Start;

import java.util.Map;
import java.util.Objects;

/** Registers the Controller-bound, zero-argument {@code runApprovedGate} Workflow. */
public final class IssueApprovedGateWorkflow {
    /** Public Workflow name exposed to the Agent. */
    public static final String NAME = "runApprovedGate";

    private IssueApprovedGateWorkflow() {
    }

    /** Register one job-bound Gate Workflow. */
    public static Registration register(ReActAgent agent, String agentId, GateInvoker invoker) {
        Objects.requireNonNull(agent, "agent must not be null");
        String workflowId = agentId + ".approved_gate";
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .name(NAME)
                .description("Run the immutable Controller-approved bugfix verification")
                .inputParams(inputSchema())
                .build();
        Runner.resourceMgr().removeWorkflow(workflowId, agentId,
                TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addWorkflow(card,
                () -> workflow(card, Objects.requireNonNull(invoker)), agentId);
        agent.getAbilityManager().add(card);
        return new Registration(workflowId, agentId);
    }

    /** Return the strict zero-argument Workflow schema. */
    public static Map<String, Object> inputSchema() {
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

    /** Controller callback that owns all Gate inputs. */
    @FunctionalInterface
    public interface GateInvoker {
        /** @return bounded, credential-free Gate response */
        Map<String, Object> run();
    }

    /** Removes the job-only Workflow after the complete Agent lifecycle. */
    public record Registration(String workflowId, String agentId) implements AutoCloseable {
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
            return invoker.run();
        }
    }
}
