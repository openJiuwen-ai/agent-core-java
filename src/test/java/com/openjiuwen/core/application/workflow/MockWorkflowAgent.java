/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

/**
 * MockWorkflowAgent test double documentation.
 *
 * <p>In Python, mock_workflow_agent.py is a test double that wraps
 * ControllerAgent for testing WorkflowAgent behavior without real LLM calls.
 *
 * <p>In Java, the real WorkflowAgent is directly testable with mock nodes
 * (no LLM needed for simple workflows). This class serves as a marker
 * for the translated test infrastructure.
 *
 * <p>Mirrors Python's {@code mock_workflow_agent.py} in
 * {@code tests/unit_tests/agent/workflow_agent/}.
 *
 * <p>The actual test logic is in the companion test classes:
 * <ul>
 *   <li>{@link MockWorkflowAgentInvokeTest}</li>
 *   <li>{@link MockWorkflowAgentStreamTest}</li>
 *   <li>{@link MockWorkflowAgentConcurrentTest}</li>
 *   <li>{@link MockWorkflowAgentInterruptInvokeTest}</li>
 *   <li>{@link MockWorkflowAgentInterruptStreamTest}</li>
 *   <li>{@link MockWorkflowAgentMultiInterruptTest}</li>
 *   <li>{@link MockWorkflowAgentMultiWorkflowTest}</li>
 *   <li>{@link MockWorkflowAgentMultiWorkflowDefaultResponseTest}</li>
 * </ul>
 */
final class MockWorkflowAgent {
    private MockWorkflowAgent() {}
}
