/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external;

import java.util.concurrent.CompletionStage;

/**
 * Wakeup callback invoked on each relevant transport event during watch.
 *
 * <p>Mirrors Python's {@code InboxObserver} in
 * {@code openjiuwen/agent_teams/external/client.py}.</p>
 */
@FunctionalInterface
public interface InboxObserver {

    CompletionStage<Void> observe(InboxView view);
}
