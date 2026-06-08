/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Discriminated union of supported interact payload shapes.
 *
 * <p>Mirrors Python's {@code InteractPayload} in
 * {@code openjiuwen/agent_teams/interaction/payload.py}.</p>
 */
public sealed interface InteractPayload
        permits GodViewMessage, OperatorMessage, HumanAgentMessage {
}
