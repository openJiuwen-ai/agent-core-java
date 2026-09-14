/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

/**
 * Internal timeout signal for one-shot external CLI turns.
 *
 * <p>Mirrors Python's {@code _TurnTimeout} in
 * {@code openjiuwen/agent_teams/external/runtime.py}.</p>
 */
final class CliTurnTimeout extends RuntimeException {

    private final boolean absolute;

    CliTurnTimeout(boolean absolute) {
        super("turn timeout");
        this.absolute = absolute;
    }

    boolean isAbsolute() {
        return absolute;
    }
}
