/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.agent;

/**
 * Workflow id/version pair consumed by legacy workflow removal APIs.
 *
 * <p>Mirrors Python's {@code Tuple[str, str]} workflow references in
 * {@code openjiuwen/core/single_agent/legacy/agent.py}.</p>
 */
public record WorkflowReference(String workflowId, String workflowVersion) {
}
