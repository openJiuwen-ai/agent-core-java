/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Async graph-state storage abstraction.
 * <p>
 * Mirrors Python's {@code Store} in
 * {@code openjiuwen/core/graph/store/base.py}.
 */
public interface Store {

    CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns);

    CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state);

    CompletionStage<Void> delete(String sessionId, String ns);
}
