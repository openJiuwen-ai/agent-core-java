/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

/**
 * Active human-agent inbox subscription installed by {@code /team watch}.
 *
 * <p>Mirrors Python's {@code WatchBinding} in
 * {@code openjiuwen/agent_teams/cli/state.py}.</p>
 */
public record WatchBinding(String teamName, String sessionId, String memberName) {
}
