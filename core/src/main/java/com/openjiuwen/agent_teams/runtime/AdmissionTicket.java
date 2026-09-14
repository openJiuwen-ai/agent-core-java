/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

/**
 * Opaque token returned from a successful admit call.
 *
 * <p>Mirrors Python's {@code AdmissionTicket} in
 * {@code openjiuwen/agent_teams/runtime/gate.py}.</p>
 */
public record AdmissionTicket(InteractGate gate) {
}
