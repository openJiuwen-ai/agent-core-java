/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Optional;

/**
 * Decorator around {@link Store} that adds logging for graph state operations.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.base.GraphStore}.
 */
public class GraphStore implements Store {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Store delegate;

    public GraphStore(Store delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<GraphStoreState> get(String sessionId, String ns) {
        try {
            Optional<GraphStoreState> state = delegate.get(sessionId, ns);
            if (state.isEmpty()) {
                logger.debug("Not found graph state for session, sessionId={}, ns={}", sessionId, ns);
            }
            return state;
        } catch (Exception e) {
            logger.error("Failed to get graph state, sessionId={}, ns={}", sessionId, ns, e);
            throw e;
        }
    }

    @Override
    public void save(String sessionId, String ns, GraphStoreState state) {
        logger.debug("Begin to save graph state of super-step[{}], sessionId={}, ns={}",
                state.getStep(), sessionId, ns);
        try {
            delegate.save(sessionId, ns, state);
            logger.debug("Succeed to save graph state of super-step[{}], sessionId={}, ns={}",
                    state.getStep(), sessionId, ns);
        } catch (Exception e) {
            logger.error("Failed to save graph state of super-step[{}], sessionId={}, ns={}",
                    state.getStep(), sessionId, ns, e);
            throw e;
        }
    }

    @Override
    public void delete(String sessionId, String ns) {
        logger.debug("Begin to delete {} graph states for session, sessionId={}",
                ns != null ? ns : "all", sessionId);
        try {
            delegate.delete(sessionId, ns);
            logger.debug("Succeed to delete {} graph states for session, sessionId={}",
                    ns != null ? ns : "all", sessionId);
        } catch (Exception e) {
            logger.error("Failed to delete {} graph states for session, sessionId={}",
                    ns != null ? ns : "all", sessionId, e);
            throw e;
        }
    }
}
