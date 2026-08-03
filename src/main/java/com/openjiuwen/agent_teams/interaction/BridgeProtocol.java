/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

/**
 * Bridge-agent protocol module constants.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.interaction.bridge_protocol} in
 * {@code openjiuwen/agent_teams/interaction/bridge_protocol.py}.</p>
 */
public final class BridgeProtocol {
    public static final String REMOTE_UNAVAILABLE_SENTINEL =
            "[remote agent unavailable: no protocol adapter registered]";

    private BridgeProtocol() {
    }
}
