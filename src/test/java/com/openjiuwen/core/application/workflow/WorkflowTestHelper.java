/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import java.util.Map;

final class WorkflowTestHelper {

    private WorkflowTestHelper() {}

    static Workflow buildSimpleWorkflow(String workflowId, String name) {
        WorkflowCard card = WorkflowCard.builder()
                .id(workflowId)
                .version("1.0")
                .name(name)
                .description("Simple workflow for test")
                .build();
        Workflow flow = new Workflow(card);

        flow.setStartComp("start", new Start(),
                Map.of("query", "${query}"), null);
        flow.addWorkflowComp("node_a", new IdentityNode(),
                Map.of("output", "${start.query}"), null);
        flow.setEndComp("end", new PassThroughEndNode(),
                Map.of("result", "${node_a.output}"), null);

        flow.addConnection("start", "node_a");
        flow.addConnection("node_a", "end");
        return flow;
    }

    static final class IdentityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    static final class PassThroughEndNode extends End {
        public PassThroughEndNode() {
            super(Map.of("responseTemplate", "result: {{result}}"));
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }
}
