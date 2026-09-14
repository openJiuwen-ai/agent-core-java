/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

/**
 * Tuple key for {@link WatchBinding} maps.
 *
 * <p>Mirrors Python's {@code tuple[str, str, str]} watch binding key in
 * {@code openjiuwen/agent_teams/cli/state.py}.</p>
 */
public record WatchBindingKey(String teamName, String sessionId, String memberName) {

    public static WatchBindingKey from(WatchBinding binding) {
        return new WatchBindingKey(binding.teamName(), binding.sessionId(), binding.memberName());
    }
}
