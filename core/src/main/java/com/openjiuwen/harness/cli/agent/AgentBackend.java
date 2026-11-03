/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import java.util.Iterator;
import java.util.concurrent.CompletionStage;

/**
 * Backend abstraction over local or remote CLI agent execution.
 *
 * <p>Mirrors Python's {@code AgentBackend} protocol in
 * {@code openjiuwen/harness/cli/agent/factory.py}.</p>
 */
public interface AgentBackend {

    CompletionStage<Void> start();

    CompletionStage<Void> stop();

    CompletionStage<Iterator<Object>> runStreaming(Object query, String sessionId);

    CompletionStage<Void> abort();
}
