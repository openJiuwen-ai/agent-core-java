/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;

import java.util.List;

/**
 * Snapshot of what currently needs the member's attention.
 *
 * <p>Mirrors Python's {@code InboxView} in
 * {@code openjiuwen/agent_teams/external/client.py}.</p>
 */
public record InboxView(List<TeamMessage> messages, List<TeamTask> tasks) {

    public InboxView {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    public boolean isEmpty() {
        return messages.isEmpty() && tasks.isEmpty();
    }
}
